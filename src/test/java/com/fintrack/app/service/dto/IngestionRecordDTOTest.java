package com.fintrack.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class IngestionRecordDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(IngestionRecordDTO.class);
        IngestionRecordDTO ingestionRecordDTO1 = new IngestionRecordDTO();
        ingestionRecordDTO1.setId(1L);
        IngestionRecordDTO ingestionRecordDTO2 = new IngestionRecordDTO();
        assertThat(ingestionRecordDTO1).isNotEqualTo(ingestionRecordDTO2);
        ingestionRecordDTO2.setId(ingestionRecordDTO1.getId());
        assertThat(ingestionRecordDTO1).isEqualTo(ingestionRecordDTO2);
        ingestionRecordDTO2.setId(2L);
        assertThat(ingestionRecordDTO1).isNotEqualTo(ingestionRecordDTO2);
        ingestionRecordDTO1.setId(null);
        assertThat(ingestionRecordDTO1).isNotEqualTo(ingestionRecordDTO2);
    }
}
