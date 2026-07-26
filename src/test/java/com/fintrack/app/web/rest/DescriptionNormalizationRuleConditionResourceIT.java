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
import com.fintrack.app.service.dto.DescriptionNormalizationRuleConditionDTO;
import com.fintrack.app.service.mapper.DescriptionNormalizationRuleConditionMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
class DescriptionNormalizationRuleConditionResourceIT {

    private static final String ENTITY_API_URL = "/api/description-normalization-rule-conditions";

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
    private DescriptionNormalizationRuleConditionMapper conditionMapper;

    @Test
    @Transactional
    void createConditionAppendsPositionAndTrimsValue() throws Exception {
        DescriptionNormalizationRule rule = persistRule("Rule", 0);
        persistCondition(rule, 0, "Existing");
        DescriptionNormalizationRuleConditionDTO dto = conditionMapper.toDto(condition(rule, 999, "  Uber  "));

        mockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.value").value("Uber"))
            .andExpect(jsonPath("$.position").value(1));
    }

    @Test
    @Transactional
    void requiredFieldsAreValidated() throws Exception {
        DescriptionNormalizationRule rule = persistRule("Rule", 0);

        DescriptionNormalizationRuleConditionDTO blankValue = conditionMapper.toDto(condition(rule, 0, "   "));
        mockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(blankValue)))
            .andExpect(status().isBadRequest());

        DescriptionNormalizationRuleConditionDTO missingOperator = conditionMapper.toDto(condition(rule, 0, "Uber"));
        missingOperator.setOperator(null);
        mockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(missingOperator)))
            .andExpect(status().isBadRequest());

        DescriptionNormalizationRuleConditionDTO missingParent = conditionMapper.toDto(condition(rule, 0, "Uber"));
        missingParent.setDescriptionNormalizationRule(null);
        mockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(missingParent)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void ownershipThroughParentIsEnforced() throws Exception {
        DescriptionNormalizationRule foreignRule = rule("Foreign", 0);
        foreignRule.setUser(createOtherUser());
        foreignRule = ruleRepository.saveAndFlush(foreignRule);
        DescriptionNormalizationRuleConditionDTO dto = conditionMapper.toDto(condition(foreignRule, 0, "Uber"));

        mockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updatePreservesServerOwnedFields() throws Exception {
        DescriptionNormalizationRule rule = persistRule("Rule", 0);
        DescriptionNormalizationRuleCondition condition = persistCondition(rule, 0, "Uber");
        Instant createdAt = condition.getCreatedAt();
        Instant updatedAt = condition.getUpdatedAt();

        DescriptionNormalizationRuleConditionDTO dto = conditionMapper.toDto(condition);
        dto.setValue("  Lyft  ");

        mockMvc
            .perform(
                put(ENTITY_API_URL + "/{id}", condition.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.value").value("Lyft"));

        DescriptionNormalizationRuleCondition persisted = conditionRepository.findById(condition.getId()).orElseThrow();
        assertThat(persisted.getCreatedAt()).isEqualTo(createdAt);
        assertThat(persisted.getUpdatedAt()).isAfterOrEqualTo(updatedAt);
        assertThat(persisted.getPosition()).isZero();
        assertThat(persisted.getDescriptionNormalizationRule().getId()).isEqualTo(rule.getId());
    }

    @Test
    @Transactional
    void patchPreservesServerOwnedFields() throws Exception {
        DescriptionNormalizationRule rule = persistRule("Rule", 0);
        DescriptionNormalizationRuleCondition condition = persistCondition(rule, 0, "Uber");
        Instant createdAt = condition.getCreatedAt();
        Instant updatedAt = condition.getUpdatedAt();

        mockMvc
            .perform(
                patch(ENTITY_API_URL + "/{id}", condition.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(Map.of("id", condition.getId(), "value", "  Lyft  ")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.value").value("Lyft"));

        DescriptionNormalizationRuleCondition persisted = conditionRepository.findById(condition.getId()).orElseThrow();
        assertThat(persisted.getCreatedAt()).isEqualTo(createdAt);
        assertThat(persisted.getUpdatedAt()).isAfterOrEqualTo(updatedAt);
        assertThat(persisted.getPosition()).isZero();
        assertThat(persisted.getDescriptionNormalizationRule().getId()).isEqualTo(rule.getId());
    }

    @Test
    @Transactional
    void deleteWorksAndParentCanRemainInactiveWithoutConditions() throws Exception {
        DescriptionNormalizationRule rule = persistRule("Rule", 0);
        DescriptionNormalizationRuleCondition condition = persistCondition(rule, 0, "Uber");

        mockMvc.perform(delete(ENTITY_API_URL + "/{id}", condition.getId())).andExpect(status().isNoContent());

        assertThat(conditionRepository.findById(condition.getId())).isEmpty();
        assertThat(ruleRepository.findById(rule.getId()).orElseThrow().getActive()).isFalse();
    }

    static DescriptionNormalizationRuleCondition createEntity(EntityManager em) {
        DescriptionNormalizationRule rule = DescriptionNormalizationRuleResourceIT.createEntity(em);
        em.persist(rule);
        em.flush();
        return new DescriptionNormalizationRuleCondition()
            .operator(DescriptionNormalizationRuleOperator.CONTAINS)
            .value("Uber")
            .caseSensitive(false)
            .position(0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .descriptionNormalizationRule(rule);
    }

    private DescriptionNormalizationRule rule(String name, int priority) {
        DescriptionNormalizationRule rule = new DescriptionNormalizationRule()
            .name(name)
            .description(null)
            .active(false)
            .priority(priority)
            .conditionOperator(RuleConditionLogic.ALL)
            .resultingDescription("Uber")
            .createdAt(Instant.now())
            .updatedAt(Instant.now());
        rule.setUser(currentUser());
        return rule;
    }

    private DescriptionNormalizationRule persistRule(String name, int priority) {
        return ruleRepository.saveAndFlush(rule(name, priority));
    }

    private DescriptionNormalizationRuleCondition condition(DescriptionNormalizationRule rule, int position, String value) {
        return new DescriptionNormalizationRuleCondition()
            .operator(DescriptionNormalizationRuleOperator.CONTAINS)
            .value(value)
            .caseSensitive(false)
            .position(position)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .descriptionNormalizationRule(rule);
    }

    private DescriptionNormalizationRuleCondition persistCondition(DescriptionNormalizationRule rule, int position, String value) {
        DescriptionNormalizationRuleCondition persisted = conditionRepository.saveAndFlush(condition(rule, position, value));
        rule.getConditions().add(persisted);
        return persisted;
    }

    private User currentUser() {
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
