package com.fintrack.app.repository;

import com.fintrack.app.domain.ApiAccessToken;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/**
 * Utility repository to load bag relationships based on https://vladmihalcea.com/hibernate-multiplebagfetchexception/
 */
public class ApiAccessTokenRepositoryWithBagRelationshipsImpl implements ApiAccessTokenRepositoryWithBagRelationships {

    private static final String ID_PARAMETER = "id";
    private static final String APIACCESSTOKENS_PARAMETER = "apiAccessTokens";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<ApiAccessToken> fetchBagRelationships(Optional<ApiAccessToken> apiAccessToken) {
        return apiAccessToken.map(this::fetchAccounts);
    }

    @Override
    public Page<ApiAccessToken> fetchBagRelationships(Page<ApiAccessToken> apiAccessTokens) {
        return new PageImpl<>(
            fetchBagRelationships(apiAccessTokens.getContent()),
            apiAccessTokens.getPageable(),
            apiAccessTokens.getTotalElements()
        );
    }

    @Override
    public List<ApiAccessToken> fetchBagRelationships(List<ApiAccessToken> apiAccessTokens) {
        return Optional.of(apiAccessTokens).map(this::fetchAccounts).orElse(Collections.emptyList());
    }

    ApiAccessToken fetchAccounts(ApiAccessToken result) {
        return entityManager
            .createQuery(
                "select apiAccessToken from ApiAccessToken apiAccessToken left join fetch apiAccessToken.accounts where apiAccessToken.id = :id",
                ApiAccessToken.class
            )
            .setParameter(ID_PARAMETER, result.getId())
            .getSingleResult();
    }

    List<ApiAccessToken> fetchAccounts(List<ApiAccessToken> apiAccessTokens) {
        HashMap<Object, Integer> order = new HashMap<>();
        IntStream.range(0, apiAccessTokens.size()).forEach(index -> order.put(apiAccessTokens.get(index).getId(), index));
        List<ApiAccessToken> result = entityManager
            .createQuery(
                "select apiAccessToken from ApiAccessToken apiAccessToken left join fetch apiAccessToken.accounts where apiAccessToken in :apiAccessTokens",
                ApiAccessToken.class
            )
            .setParameter(APIACCESSTOKENS_PARAMETER, apiAccessTokens)
            .getResultList();
        Collections.sort(result, (o1, o2) -> Integer.compare(order.get(o1.getId()), order.get(o2.getId())));
        return result;
    }
}
