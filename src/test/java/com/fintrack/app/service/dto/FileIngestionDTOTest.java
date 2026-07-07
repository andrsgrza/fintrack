package com.fintrack.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class FileIngestionDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(FileIngestionDTO.class);
        FileIngestionDTO fileIngestionDTO1 = new FileIngestionDTO();
        fileIngestionDTO1.setId(1L);
        FileIngestionDTO fileIngestionDTO2 = new FileIngestionDTO();
        assertThat(fileIngestionDTO1).isNotEqualTo(fileIngestionDTO2);
        fileIngestionDTO2.setId(fileIngestionDTO1.getId());
        assertThat(fileIngestionDTO1).isEqualTo(fileIngestionDTO2);
        fileIngestionDTO2.setId(2L);
        assertThat(fileIngestionDTO1).isNotEqualTo(fileIngestionDTO2);
        fileIngestionDTO1.setId(null);
        assertThat(fileIngestionDTO1).isNotEqualTo(fileIngestionDTO2);
    }
}
