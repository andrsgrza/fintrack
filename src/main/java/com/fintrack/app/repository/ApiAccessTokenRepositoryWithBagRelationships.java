package com.fintrack.app.repository;

import com.fintrack.app.domain.ApiAccessToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;

public interface ApiAccessTokenRepositoryWithBagRelationships {
    Optional<ApiAccessToken> fetchBagRelationships(Optional<ApiAccessToken> apiAccessToken);

    List<ApiAccessToken> fetchBagRelationships(List<ApiAccessToken> apiAccessTokens);

    Page<ApiAccessToken> fetchBagRelationships(Page<ApiAccessToken> apiAccessTokens);
}
