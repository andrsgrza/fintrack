package com.fintrack.app.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.DescriptionNormalizationRule;
import com.fintrack.app.domain.DescriptionNormalizationRuleCondition;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.DescriptionNormalizationRuleOperator;
import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import com.fintrack.app.repository.DescriptionNormalizationRuleConditionRepository;
import com.fintrack.app.repository.DescriptionNormalizationRuleRepository;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleDTO;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleReorderRequestDTO;
import com.fintrack.app.service.mapper.DescriptionNormalizationRuleMapper;
import com.fintrack.app.service.rules.DescriptionNormalizationRuleEvaluationService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class DescriptionNormalizationRuleResourceIT {

    private static final String ENTITY_API_URL = "/api/description-normalization-rules";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptionNormalizationRuleRepository ruleRepository;

    @Autowired
    private DescriptionNormalizationRuleConditionRepository conditionRepository;

    @Autowired
    private DescriptionNormalizationRuleMapper ruleMapper;

    @Autowired
    private DescriptionNormalizationRuleEvaluationService evaluationService;

    @Test
    @Transactional
    void configuredCreateAtomicallyCreatesAnActiveRuleWithOrderedConditionsAndCanBeEvaluated() throws Exception {
        persistRule("Existing", 0);
        Map<String, Object> request = Map.of(
            "name",
            "  Normalize Uber  ",
            "description",
            "  From the ingestion review  ",
            "active",
            true,
            "conditionOperator",
            "ALL",
            "resultingDescription",
            "  Uber  ",
            "conditions",
            List.of(
                Map.of("operator", "CONTAINS", "value", "  Uber  ", "caseSensitive", false),
                Map.of("operator", "CONTAINS", "value", "trip", "caseSensitive", false)
            )
        );

        mockMvc
            .perform(post(ENTITY_API_URL + "/configured").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Normalize Uber"))
            .andExpect(jsonPath("$.description").value("From the ingestion review"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.priority").value(1))
            .andExpect(jsonPath("$.resultingDescription").value("Uber"))
            .andExpect(jsonPath("$.conditions[0].position").value(0))
            .andExpect(jsonPath("$.conditions[1].position").value(1));

        DescriptionNormalizationRule configured = ruleRepository.findByUserIdOrderByPriorityAscIdAsc(currentUser(em).getId()).get(1);
        assertThat(conditionRepository.findByDescriptionNormalizationRuleIdOrderByPositionAscIdAsc(configured.getId()))
            .extracting(DescriptionNormalizationRuleCondition::getValue)
            .containsExactly("Uber", "trip");
        em.flush();
        em.clear();
        assertThat(evaluationService.evaluate("user", "Uber trip").resultingDescription()).isEqualTo("Uber");
    }

    @Test
    @Transactional
    void configuredCreateRejectsInvalidActivePayloadWithoutPersistingAnOrphanRule() throws Exception {
        long rulesBefore = ruleRepository.count();
        Map<String, Object> invalidRequest = Map.of(
            "name",
            "Invalid configured rule",
            "active",
            true,
            "conditionOperator",
            "ALL",
            "resultingDescription",
            "Uber",
            "conditions",
            List.of(Map.of("operator", "CONTAINS", "value", "   ", "caseSensitive", false))
        );

        mockMvc
            .perform(
                post(ENTITY_API_URL + "/configured").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(invalidRequest))
            )
            .andExpect(status().isBadRequest());

        assertThat(ruleRepository.count()).isEqualTo(rulesBefore);
        assertThat(conditionRepository.count()).isZero();
    }

    @Test
    @Transactional
    void createInactiveDraftTrimsFieldsAndAppendsPriority() throws Exception {
        persistRule("Existing", 0);
        DescriptionNormalizationRuleDTO dto = ruleMapper.toDto(rule("  Uber Normalize  ", 99));
        dto.setDescription("  imported uber rows  ");
        dto.setResultingDescription("  Uber  ");

        mockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Uber Normalize"))
            .andExpect(jsonPath("$.description").value("imported uber rows"))
            .andExpect(jsonPath("$.resultingDescription").value("Uber"))
            .andExpect(jsonPath("$.active").value(false))
            .andExpect(jsonPath("$.priority").value(1));
    }

    @Test
    @Transactional
    void nameIsRequiredAndUniquePerOwner() throws Exception {
        persistRule("Normalize Uber", 0);

        DescriptionNormalizationRuleDTO blank = ruleMapper.toDto(rule("   ", 0));
        mockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(blank)))
            .andExpect(status().isBadRequest());

        DescriptionNormalizationRuleDTO duplicate = ruleMapper.toDto(rule(" normalize uber ", 0));
        mockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(duplicate)))
            .andExpect(status().isBadRequest());

        User otherUser = createOtherUser();
        DescriptionNormalizationRule sameNameOtherOwner = rule("Normalize Uber", 0);
        sameNameOtherOwner.setUser(otherUser);
        ruleRepository.saveAndFlush(sameNameOtherOwner);
        assertThat(ruleRepository.findAll()).hasSize(2);
    }

    @Test
    @Transactional
    void resultingDescriptionIsRequiredTrimmedAndNonblank() throws Exception {
        DescriptionNormalizationRuleDTO blank = ruleMapper.toDto(rule("Rule", 0));
        blank.setResultingDescription("   ");
        mockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(blank)))
            .andExpect(status().isBadRequest());

        DescriptionNormalizationRuleDTO trimmed = ruleMapper.toDto(rule("Rule", 0));
        trimmed.setResultingDescription("  Uber  ");
        mockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(trimmed)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resultingDescription").value("Uber"));
    }

    @Test
    @Transactional
    void activeTrueRequiresAtLeastOneCondition() throws Exception {
        DescriptionNormalizationRule persisted = persistRule("Inactive", 0);
        mockMvc
            .perform(
                patch(ENTITY_API_URL + "/{id}", persisted.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(Map.of("id", persisted.getId(), "active", true)))
            )
            .andExpect(status().isBadRequest());

        createCondition(persisted, 0);
        mockMvc
            .perform(
                patch(ENTITY_API_URL + "/{id}", persisted.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(Map.of("id", persisted.getId(), "active", true)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @Transactional
    void reorderValidatesCurrentUserCompleteRuleSet() throws Exception {
        DescriptionNormalizationRule first = persistRule("First", 0);
        DescriptionNormalizationRule second = persistRule("Second", 1);
        DescriptionNormalizationRule foreign = rule("Foreign", 0);
        foreign.setUser(createOtherUser());
        foreign = ruleRepository.saveAndFlush(foreign);

        DescriptionNormalizationRuleReorderRequestDTO incomplete = new DescriptionNormalizationRuleReorderRequestDTO();
        incomplete.setOrderedIds(List.of(first.getId()));
        mockMvc
            .perform(put(ENTITY_API_URL + "/reorder").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(incomplete)))
            .andExpect(status().isBadRequest());

        DescriptionNormalizationRuleReorderRequestDTO foreignRequest = new DescriptionNormalizationRuleReorderRequestDTO();
        foreignRequest.setOrderedIds(List.of(second.getId(), first.getId(), foreign.getId()));
        mockMvc
            .perform(put(ENTITY_API_URL + "/reorder").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(foreignRequest)))
            .andExpect(status().isBadRequest());

        DescriptionNormalizationRuleReorderRequestDTO valid = new DescriptionNormalizationRuleReorderRequestDTO();
        valid.setOrderedIds(List.of(second.getId(), first.getId()));
        mockMvc
            .perform(put(ENTITY_API_URL + "/reorder").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(valid)))
            .andExpect(status().isOk());

        assertThat(ruleRepository.findById(second.getId()).orElseThrow().getPriority()).isZero();
        assertThat(ruleRepository.findById(first.getId()).orElseThrow().getPriority()).isEqualTo(1);
    }

    @Test
    @Transactional
    void deleteReindexesPriorityAndOwnershipIsEnforced() throws Exception {
        DescriptionNormalizationRule first = persistRule("First", 0);
        DescriptionNormalizationRule second = persistRule("Second", 1);
        DescriptionNormalizationRule third = persistRule("Third", 2);
        DescriptionNormalizationRule foreign = rule("Foreign", 0);
        foreign.setUser(createOtherUser());
        foreign = ruleRepository.saveAndFlush(foreign);

        mockMvc.perform(delete(ENTITY_API_URL + "/{id}", foreign.getId())).andExpect(status().isBadRequest());
        mockMvc.perform(delete(ENTITY_API_URL + "/{id}", second.getId())).andExpect(status().isNoContent());

        assertThat(ruleRepository.findById(first.getId()).orElseThrow().getPriority()).isZero();
        assertThat(ruleRepository.findById(third.getId()).orElseThrow().getPriority()).isEqualTo(1);
    }

    static DescriptionNormalizationRule createEntity(EntityManager em) {
        DescriptionNormalizationRule rule = new DescriptionNormalizationRule()
            .name("Normalize Uber")
            .description("Uber rows")
            .active(false)
            .priority(0)
            .conditionOperator(RuleConditionLogic.ALL)
            .resultingDescription("Uber")
            .createdAt(Instant.now())
            .updatedAt(Instant.now());
        rule.setUser(currentUser(em));
        return rule;
    }

    private DescriptionNormalizationRule rule(String name, int priority) {
        DescriptionNormalizationRule rule = createEntity(em);
        rule.setName(name);
        rule.setPriority(priority);
        return rule;
    }

    private DescriptionNormalizationRule persistRule(String name, int priority) {
        return ruleRepository.saveAndFlush(rule(name, priority));
    }

    private DescriptionNormalizationRuleCondition createCondition(DescriptionNormalizationRule rule, int position) {
        DescriptionNormalizationRuleCondition condition = new DescriptionNormalizationRuleCondition()
            .operator(DescriptionNormalizationRuleOperator.CONTAINS)
            .value("Uber")
            .caseSensitive(false)
            .position(position)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .descriptionNormalizationRule(rule);
        DescriptionNormalizationRuleCondition persisted = conditionRepository.saveAndFlush(condition);
        rule.getConditions().add(persisted);
        return persisted;
    }

    private static User currentUser(EntityManager em) {
        return em
            .createQuery("select user from User user where user.login = :login", User.class)
            .setParameter("login", "user")
            .getSingleResult();
    }

    private User createOtherUser() {
        User otherUser = UserResourceIT.createEntity();
        em.persist(otherUser);
        em.flush();
        return otherUser;
    }
}
