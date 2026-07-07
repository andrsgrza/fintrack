package com.fintrack.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ApiIngestionDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(ApiIngestionDTO.class);
        ApiIngestionDTO apiIngestionDTO1 = new ApiIngestionDTO();
        apiIngestionDTO1.setId(1L);
        ApiIngestionDTO apiIngestionDTO2 = new ApiIngestionDTO();
        assertThat(apiIngestionDTO1).isNotEqualTo(apiIngestionDTO2);
        apiIngestionDTO2.setId(apiIngestionDTO1.getId());
        assertThat(apiIngestionDTO1).isEqualTo(apiIngestionDTO2);
        apiIngestionDTO2.setId(2L);
        assertThat(apiIngestionDTO1).isNotEqualTo(apiIngestionDTO2);
        apiIngestionDTO1.setId(null);
        assertThat(apiIngestionDTO1).isNotEqualTo(apiIngestionDTO2);
    }
}
