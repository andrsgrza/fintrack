package com.fintrack.app.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleDTO;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleReorderRequestDTO;
import com.fintrack.app.service.mapper.DescriptionNormalizationRuleMapper;
import com.fintrack.app.service.rules.DescriptionNormalizationRuleEvaluationService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
    void configuredUpdateReplacesTheCompleteConditionSetPreservesOwnerAndPriorityAndEvaluatesTheNewRule() throws Exception {
        DescriptionNormalizationRule persisted = persistRule("Normalize ride", 7);
        Instant createdAt = persisted.getCreatedAt();
        Long ownerId = persisted.getUser().getId();
        createCondition(persisted, 0).setValue("old merchant");
        conditionRepository.flush();

        Map<String, Object> request = configuredRequest(
            "Normalize ride updated",
            "Changed as one configured aggregate",
            true,
            "ANY",
            "Ride share",
            List.of(configuredCondition("STARTS_WITH", "LYFT", false), configuredCondition("REGEX", "UBER\\s+TRIP", false))
        );

        mockMvc
            .perform(
                put(ENTITY_API_URL + "/{id}/configured", persisted.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(request))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Normalize ride updated"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.priority").value(7))
            .andExpect(jsonPath("$.conditions.length()").value(2))
            .andExpect(jsonPath("$.conditions[0].operator").value("STARTS_WITH"))
            .andExpect(jsonPath("$.conditions[1].operator").value("REGEX"));

        mockMvc
            .perform(get(ENTITY_API_URL + "/{id}/configured", persisted.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priority").value(7))
            .andExpect(jsonPath("$.conditions[0].value").value("LYFT"))
            .andExpect(jsonPath("$.conditions[1].value").value("UBER\\s+TRIP"));

        em.flush();
        em.clear();
        DescriptionNormalizationRule updated = ruleRepository.findById(persisted.getId()).orElseThrow();
        assertThat(updated.getUser().getId()).isEqualTo(ownerId);
        assertThat(updated.getPriority()).isEqualTo(7);
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(conditionRepository.findByDescriptionNormalizationRuleIdOrderByPositionAscIdAsc(updated.getId()))
            .extracting(DescriptionNormalizationRuleCondition::getValue)
            .containsExactly("LYFT", "UBER\\s+TRIP");
        assertThat(evaluationService.evaluate("user", "lyft trip").resultingDescription()).isEqualTo("Ride share");
    }

    @Test
    @Transactional
    void configuredUpdateValidatesBeforeReplacingConditions() throws Exception {
        DescriptionNormalizationRule persisted = persistRule("Normalize merchant", 3);
        createCondition(persisted, 0).setValue("original value");
        conditionRepository.flush();

        Map<String, Object> invalidRequest = configuredRequest(
            "A changed name which must not persist",
            null,
            true,
            "ALL",
            "Updated",
            List.of(configuredCondition("CONTAINS", " ", false))
        );
        mockMvc
            .perform(
                put(ENTITY_API_URL + "/{id}/configured", persisted.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(invalidRequest))
            )
            .andExpect(status().isBadRequest());

        mockMvc
            .perform(put(ENTITY_API_URL + "/{id}/configured", persisted.getId()).contentType(MediaType.APPLICATION_JSON).content("null"))
            .andExpect(status().isBadRequest());

        em.clear();
        assertThat(conditionRepository.findByDescriptionNormalizationRuleIdOrderByPositionAscIdAsc(persisted.getId()))
            .extracting(DescriptionNormalizationRuleCondition::getValue)
            .containsExactly("original value");
        assertThat(ruleRepository.findById(persisted.getId()).orElseThrow().getName()).isEqualTo("Normalize merchant");
    }

    @Test
    @Transactional
    void repeatedConfiguredUpdatesDoNotAccumulateConditions() throws Exception {
        DescriptionNormalizationRule persisted = persistRule("Normalize merchant", 3);
        createCondition(persisted, 0).setValue("original value");
        conditionRepository.flush();

        Map<String, Object> firstValidRequest = configuredRequest(
            "Normalize merchant",
            null,
            false,
            "ALL",
            "Merchant",
            List.of(configuredCondition("CONTAINS", "one", false), configuredCondition("ENDS_WITH", "two", true))
        );
        putConfigured(persisted.getId(), firstValidRequest).andExpect(status().isOk());
        Map<String, Object> secondValidRequest = configuredRequest(
            "Normalize merchant",
            null,
            false,
            "ANY",
            "Merchant",
            List.of(configuredCondition("EXACT", "only final condition", false))
        );
        putConfigured(persisted.getId(), secondValidRequest).andExpect(status().isOk());

        em.flush();
        em.clear();
        assertThat(conditionRepository.findByDescriptionNormalizationRuleIdOrderByPositionAscIdAsc(persisted.getId()))
            .extracting(DescriptionNormalizationRuleCondition::getValue)
            .containsExactly("only final condition");
    }

    @Test
    @Transactional
    void configuredUpdateSupportsInactiveRulesWithoutConditionsButRejectsActivatingThem() throws Exception {
        DescriptionNormalizationRule persisted = persistRule("Inactive configurable", 0);
        Map<String, Object> inactiveRequest = configuredRequest("Inactive configurable", null, false, "ALL", "Normalized", List.of());

        putConfigured(persisted.getId(), inactiveRequest).andExpect(status().isOk()).andExpect(jsonPath("$.conditions").isEmpty());

        Map<String, Object> invalidActiveRequest = configuredRequest("Inactive configurable", null, true, "ALL", "Normalized", List.of());
        putConfigured(persisted.getId(), invalidActiveRequest).andExpect(status().isBadRequest());

        em.clear();
        assertThat(ruleRepository.findById(persisted.getId()).orElseThrow().getActive()).isFalse();
        assertThat(conditionRepository.countByDescriptionNormalizationRuleId(persisted.getId())).isZero();
    }

    @Test
    @Transactional
    void configuredEndpointsAreOwnerScopedAndReturnMissingRulesAsNotFound() throws Exception {
        DescriptionNormalizationRule foreign = rule("Foreign configured", 0);
        foreign.setUser(createOtherUser());
        foreign = ruleRepository.saveAndFlush(foreign);
        createCondition(foreign, 0).setValue("foreign value");
        conditionRepository.flush();

        Map<String, Object> request = configuredRequest(
            "Attempted update",
            null,
            true,
            "ALL",
            "Nope",
            List.of(configuredCondition("CONTAINS", "new", false))
        );
        putConfigured(foreign.getId(), request).andExpect(status().isBadRequest());
        mockMvc.perform(get(ENTITY_API_URL + "/{id}/configured", foreign.getId())).andExpect(status().isNotFound());
        putConfigured(Long.MAX_VALUE, request).andExpect(status().isBadRequest());

        assertThat(conditionRepository.findByDescriptionNormalizationRuleIdOrderByPositionAscIdAsc(foreign.getId()))
            .extracting(DescriptionNormalizationRuleCondition::getValue)
            .containsExactly("foreign value");
    }

    @Test
    @Transactional
    @WithMockUser(authorities = AuthoritiesConstants.ADMIN)
    void configuredUpdateAllowsTheExistingAdminAccessPolicyWithoutChangingForeignOwnership() throws Exception {
        DescriptionNormalizationRule foreign = rule("Foreign configured", 4);
        User otherUser = createOtherUser();
        foreign.setUser(otherUser);
        foreign = ruleRepository.saveAndFlush(foreign);
        createCondition(foreign, 0);

        putConfigured(
            foreign.getId(),
            configuredRequest("Admin updated", null, true, "ALL", "Updated", List.of(configuredCondition("CONTAINS", "admin", false)))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priority").value(4));

        em.flush();
        em.clear();
        assertThat(ruleRepository.findById(foreign.getId()).orElseThrow().getUser().getId()).isEqualTo(otherUser.getId());
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

    private ResultActions putConfigured(Long id, Map<String, Object> request) throws Exception {
        return mockMvc.perform(
            put(ENTITY_API_URL + "/{id}/configured", id).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request))
        );
    }

    private Map<String, Object> configuredRequest(
        String name,
        String description,
        boolean active,
        String conditionOperator,
        String resultingDescription,
        List<Map<String, Object>> conditions
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("description", description);
        request.put("active", active);
        request.put("conditionOperator", conditionOperator);
        request.put("resultingDescription", resultingDescription);
        request.put("conditions", conditions);
        return request;
    }

    private Map<String, Object> configuredCondition(String operator, String value, boolean caseSensitive) {
        return Map.of("operator", operator, "value", value, "caseSensitive", caseSensitive);
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
