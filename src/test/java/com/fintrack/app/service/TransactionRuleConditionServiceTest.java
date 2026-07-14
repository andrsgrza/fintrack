package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.RuleOperator;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.TransactionRuleConditionRepository;
import com.fintrack.app.repository.TransactionRuleRepository;
import com.fintrack.app.service.dto.TransactionRuleConditionDTO;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import com.fintrack.app.service.mapper.TransactionRuleConditionMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionRuleConditionServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";
    private static final String OTHER_USER_LOGIN = "other-user";

    @Mock
    private TransactionRuleConditionRepository transactionRuleConditionRepository;

    @Mock
    private TransactionRuleConditionMapper transactionRuleConditionMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private TransactionRuleRepository transactionRuleRepository;

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @InjectMocks
    private TransactionRuleConditionService transactionRuleConditionService;

    private User currentUser;
    private User otherUser;
    private TransactionRule ownRule;
    private TransactionRule otherRule;
    private TransactionRuleCondition transactionRuleCondition;
    private TransactionRuleConditionDTO transactionRuleConditionDTO;
    private TransactionRuleDTO ownRuleDTO;
    private TransactionRuleDTO otherRuleDTO;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        otherUser = new User();
        otherUser.setId(3L);
        otherUser.setLogin(OTHER_USER_LOGIN);

        ownRule = new TransactionRule();
        ownRule.setId(10L);
        ownRule.setUser(currentUser);
        ownRule.setActive(true);
        ownRule.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        otherRule = new TransactionRule();
        otherRule.setId(20L);
        otherRule.setUser(otherUser);
        otherRule.setActive(true);
        otherRule.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        transactionRuleCondition = new TransactionRuleCondition();
        transactionRuleCondition.setId(100L);
        transactionRuleCondition.setField(TransactionRuleField.DESCRIPTION);
        transactionRuleCondition.setOperator(RuleOperator.EQUALS);
        transactionRuleCondition.setValue("test");
        transactionRuleCondition.setCaseSensitive(false);
        transactionRuleCondition.setPosition(0);
        transactionRuleCondition.setTransactionRule(ownRule);

        transactionRuleConditionDTO = new TransactionRuleConditionDTO();
        transactionRuleConditionDTO.setId(100L);
        transactionRuleConditionDTO.setField(TransactionRuleField.DESCRIPTION);
        transactionRuleConditionDTO.setOperator(RuleOperator.EQUALS);
        transactionRuleConditionDTO.setValue("test");
        transactionRuleConditionDTO.setCaseSensitive(false);
        transactionRuleConditionDTO.setPosition(0);

        ownRuleDTO = new TransactionRuleDTO();
        ownRuleDTO.setId(10L);
        otherRuleDTO = new TransactionRuleDTO();
        otherRuleDTO.setId(20L);
        transactionRuleConditionDTO.setTransactionRule(ownRuleDTO);
    }

    @Test
    void saveShouldResolveAccessibleTransactionRule() {
        TransactionRuleCondition mappedEntity = new TransactionRuleCondition();
        mappedEntity.setField(TransactionRuleField.DESCRIPTION);
        mappedEntity.setOperator(RuleOperator.EQUALS);
        mappedEntity.setValue("test");
        mappedEntity.setCaseSensitive(false);
        mappedEntity.setPosition(0);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionMapper.toEntity(transactionRuleConditionDTO)).thenReturn(mappedEntity);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(ownRule)
        );
        when(transactionRuleConditionRepository.findMaxPositionByTransactionRuleId(10L)).thenReturn(Optional.empty());
        when(transactionRuleConditionRepository.findPotentialDuplicates(any(), any(), any(), any(), any())).thenReturn(
            Collections.emptyList()
        );
        when(transactionRuleConditionRepository.save(mappedEntity)).thenReturn(transactionRuleCondition);
        when(transactionRuleConditionMapper.toDto(transactionRuleCondition)).thenReturn(transactionRuleConditionDTO);

        transactionRuleConditionService.save(transactionRuleConditionDTO);

        assertThat(mappedEntity.getTransactionRule()).isEqualTo(ownRule);
        assertThat(mappedEntity.getPosition()).isZero();
        verify(transactionRuleConditionRepository).save(mappedEntity);
    }

    @Test
    void saveShouldAppendUsingMaxPositionAndIgnoreClientPosition() {
        TransactionRuleCondition mappedEntity = new TransactionRuleCondition();
        mappedEntity.setField(TransactionRuleField.DESCRIPTION);
        mappedEntity.setOperator(RuleOperator.EQUALS);
        mappedEntity.setValue("test");
        mappedEntity.setCaseSensitive(false);
        mappedEntity.setPosition(99);
        transactionRuleConditionDTO.setPosition(99);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionMapper.toEntity(transactionRuleConditionDTO)).thenReturn(mappedEntity);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(ownRule)
        );
        when(transactionRuleConditionRepository.findMaxPositionByTransactionRuleId(10L)).thenReturn(Optional.of(4));
        when(transactionRuleConditionRepository.findPotentialDuplicates(any(), any(), any(), any(), any())).thenReturn(
            Collections.emptyList()
        );
        when(transactionRuleConditionRepository.save(mappedEntity)).thenReturn(transactionRuleCondition);
        when(transactionRuleConditionMapper.toDto(transactionRuleCondition)).thenReturn(transactionRuleConditionDTO);

        transactionRuleConditionService.save(transactionRuleConditionDTO);

        assertThat(mappedEntity.getPosition()).isEqualTo(5);
        verify(transactionRuleConditionRepository).save(mappedEntity);
    }

    @Test
    void saveShouldFailWhenTransactionRuleIsNotAccessible() {
        TransactionRuleCondition mappedEntity = new TransactionRuleCondition();

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionMapper.toEntity(transactionRuleConditionDTO)).thenReturn(mappedEntity);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionRuleConditionService.save(transactionRuleConditionDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Transaction rule is not accessible");

        verify(transactionRuleConditionRepository, never()).save(any());
    }

    @Test
    void saveShouldRejectInvalidFieldOperatorCombination() {
        TransactionRuleCondition mappedEntity = new TransactionRuleCondition();
        mappedEntity.setField(TransactionRuleField.DESCRIPTION);
        mappedEntity.setOperator(RuleOperator.GREATER_THAN);
        mappedEntity.setValue("test");
        mappedEntity.setCaseSensitive(false);
        mappedEntity.setPosition(0);

        transactionRuleConditionDTO.setOperator(RuleOperator.GREATER_THAN);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionMapper.toEntity(transactionRuleConditionDTO)).thenReturn(mappedEntity);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(ownRule)
        );
        when(transactionRuleConditionRepository.findMaxPositionByTransactionRuleId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionRuleConditionService.save(transactionRuleConditionDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Operator is not allowed for field");

        verify(transactionRuleConditionRepository, never()).save(any());
    }

    @Test
    void updateShouldFailWhenConditionIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionRepository.findOneWithEagerRelationshipsByIdAndRuleUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() -> transactionRuleConditionService.update(transactionRuleConditionDTO)).isInstanceOf(
            java.util.NoSuchElementException.class
        );

        verify(transactionRuleConditionRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectChangedTransactionRule() {
        TransactionRuleCondition mappedEntity = new TransactionRuleCondition();
        mappedEntity.setField(TransactionRuleField.DESCRIPTION);
        mappedEntity.setOperator(RuleOperator.EQUALS);
        mappedEntity.setValue("test");
        mappedEntity.setCaseSensitive(false);
        mappedEntity.setPosition(0);
        transactionRuleConditionDTO.setTransactionRule(otherRuleDTO);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionRepository.findOneWithEagerRelationshipsByIdAndRuleUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRuleCondition)
        );
        when(transactionRuleConditionMapper.toEntity(transactionRuleConditionDTO)).thenReturn(mappedEntity);

        assertThatThrownBy(() -> transactionRuleConditionService.update(transactionRuleConditionDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Transaction rule cannot be changed");

        verify(transactionRuleConditionRepository, never()).save(any());
    }

    @Test
    void updateShouldPreservePositionWhenSamePositionIsProvided() {
        TransactionRuleCondition mappedEntity = new TransactionRuleCondition();
        mappedEntity.setField(TransactionRuleField.DESCRIPTION);
        mappedEntity.setOperator(RuleOperator.EQUALS);
        mappedEntity.setValue("test");
        mappedEntity.setCaseSensitive(false);
        mappedEntity.setPosition(0);
        transactionRuleConditionDTO.setPosition(0);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionRepository.findOneWithEagerRelationshipsByIdAndRuleUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRuleCondition)
        );
        when(transactionRuleConditionMapper.toEntity(transactionRuleConditionDTO)).thenReturn(mappedEntity);
        when(transactionRuleConditionRepository.findPotentialDuplicates(any(), any(), any(), any(), eq(100L))).thenReturn(
            Collections.emptyList()
        );
        when(transactionRuleConditionRepository.save(mappedEntity)).thenReturn(transactionRuleCondition);
        when(transactionRuleConditionMapper.toDto(transactionRuleCondition)).thenReturn(transactionRuleConditionDTO);

        transactionRuleConditionService.update(transactionRuleConditionDTO);

        assertThat(mappedEntity.getPosition()).isZero();
        verify(transactionRuleConditionRepository).save(mappedEntity);
    }

    @Test
    void updateShouldRejectChangedPosition() {
        TransactionRuleCondition mappedEntity = new TransactionRuleCondition();
        mappedEntity.setField(TransactionRuleField.DESCRIPTION);
        mappedEntity.setOperator(RuleOperator.EQUALS);
        mappedEntity.setValue("test");
        mappedEntity.setCaseSensitive(false);
        mappedEntity.setPosition(1);
        transactionRuleConditionDTO.setPosition(1);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionRepository.findOneWithEagerRelationshipsByIdAndRuleUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRuleCondition)
        );
        when(transactionRuleConditionMapper.toEntity(transactionRuleConditionDTO)).thenReturn(mappedEntity);

        assertThatThrownBy(() -> transactionRuleConditionService.update(transactionRuleConditionDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Position cannot be changed");

        verify(transactionRuleConditionRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldPreserveParentWhenTransactionRuleAbsent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.put("value", "updated");

        transactionRuleConditionDTO.setValue("updated");
        transactionRuleConditionDTO.setTransactionRule(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionRepository.findOneWithEagerRelationshipsByIdAndRuleUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRuleCondition)
        );
        when(transactionRuleConditionRepository.findPotentialDuplicates(any(), any(), any(), any(), eq(100L))).thenReturn(
            Collections.emptyList()
        );
        when(transactionRuleConditionRepository.save(transactionRuleCondition)).thenReturn(transactionRuleCondition);
        when(transactionRuleConditionMapper.toDto(transactionRuleCondition)).thenReturn(transactionRuleConditionDTO);

        Optional<TransactionRuleConditionDTO> result = transactionRuleConditionService.partialUpdate(
            transactionRuleConditionDTO,
            patchNode
        );

        assertThat(result).isPresent();
        assertThat(transactionRuleCondition.getTransactionRule()).isEqualTo(ownRule);
        verify(transactionRuleRepository, never()).findOneWithEagerRelationshipsByIdAndUserLogin(any(), any());
    }

    @Test
    void partialUpdateShouldRejectNullTransactionRule() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.putNull("transactionRule");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionRepository.findOneWithEagerRelationshipsByIdAndRuleUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRuleCondition)
        );

        assertThatThrownBy(() -> transactionRuleConditionService.partialUpdate(transactionRuleConditionDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Transaction rule cannot be null");

        verify(transactionRuleConditionRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectDifferentTransactionRuleId() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 200L);
        patchNode.set("transactionRule", objectMapper.createObjectNode().put("id", 21L));

        TransactionRuleCondition otherCondition = new TransactionRuleCondition();
        otherCondition.setId(200L);
        otherCondition.setField(TransactionRuleField.DESCRIPTION);
        otherCondition.setOperator(RuleOperator.EQUALS);
        otherCondition.setValue("other");
        otherCondition.setCaseSensitive(false);
        otherCondition.setPosition(0);
        otherCondition.setTransactionRule(otherRule);
        transactionRuleConditionDTO.setId(200L);
        TransactionRuleDTO secondOtherRuleDTO = new TransactionRuleDTO();
        secondOtherRuleDTO.setId(21L);
        transactionRuleConditionDTO.setTransactionRule(secondOtherRuleDTO);

        when(currentUserService.isAdmin()).thenReturn(true);
        when(transactionRuleConditionRepository.findOneWithEagerRelationships(200L)).thenReturn(Optional.of(otherCondition));

        assertThatThrownBy(() -> transactionRuleConditionService.partialUpdate(transactionRuleConditionDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Transaction rule cannot be changed");

        verify(transactionRuleConditionRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldPreservePositionWhenOmitted() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.put("value", "updated");

        transactionRuleConditionDTO.setValue("updated");
        transactionRuleConditionDTO.setPosition(null);
        transactionRuleConditionDTO.setTransactionRule(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionRepository.findOneWithEagerRelationshipsByIdAndRuleUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRuleCondition)
        );
        when(transactionRuleConditionRepository.findPotentialDuplicates(any(), any(), any(), any(), eq(100L))).thenReturn(
            Collections.emptyList()
        );
        when(transactionRuleConditionRepository.save(transactionRuleCondition)).thenReturn(transactionRuleCondition);
        when(transactionRuleConditionMapper.toDto(transactionRuleCondition)).thenReturn(transactionRuleConditionDTO);

        transactionRuleConditionService.partialUpdate(transactionRuleConditionDTO, patchNode);

        assertThat(transactionRuleCondition.getPosition()).isZero();
        verify(transactionRuleConditionRepository).save(transactionRuleCondition);
    }

    @Test
    void partialUpdateShouldRejectChangedPosition() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.put("position", 1);
        transactionRuleConditionDTO.setPosition(1);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionRepository.findOneWithEagerRelationshipsByIdAndRuleUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRuleCondition)
        );

        assertThatThrownBy(() -> transactionRuleConditionService.partialUpdate(transactionRuleConditionDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Position cannot be changed");

        verify(transactionRuleConditionRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectNullPosition() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.putNull("position");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionRepository.findOneWithEagerRelationshipsByIdAndRuleUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRuleCondition)
        );

        assertThatThrownBy(() -> transactionRuleConditionService.partialUpdate(transactionRuleConditionDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("position cannot be null");

        verify(transactionRuleConditionRepository, never()).save(any());
    }

    @Test
    void deleteShouldDeactivateParentWhenLastCondition() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionRepository.findOneWithEagerRelationshipsByIdAndRuleUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRuleCondition)
        );
        when(transactionRuleConditionRepository.countByTransactionRuleId(10L)).thenReturn(1L);
        when(transactionRuleRepository.save(ownRule)).thenReturn(ownRule);

        assertThat(transactionRuleConditionService.delete(100L)).isTrue();

        verify(transactionRuleConditionRepository).deleteById(100L);
        verify(transactionRuleRepository).save(ownRule);
        assertThat(ownRule.getActive()).isFalse();
    }

    @Test
    void deleteShouldReturnFalseWhenConditionIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionRepository.findOneWithEagerRelationshipsByIdAndRuleUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThat(transactionRuleConditionService.delete(100L)).isFalse();
        verify(transactionRuleConditionRepository, never()).deleteById(any());
    }

    @Test
    void findAllShouldUseScopedQueryForRegularUser() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleConditionRepository.findAllWithEagerRelationshipsByRuleUserLogin(CURRENT_USER_LOGIN)).thenReturn(
            java.util.List.of(transactionRuleCondition)
        );
        when(transactionRuleConditionMapper.toDto(transactionRuleCondition)).thenReturn(transactionRuleConditionDTO);

        assertThat(transactionRuleConditionService.findAll()).hasSize(1);
        verify(transactionRuleConditionRepository).findAllWithEagerRelationshipsByRuleUserLogin(CURRENT_USER_LOGIN);
        verify(transactionRuleConditionRepository, never()).findAllWithEagerRelationships();
    }
}
