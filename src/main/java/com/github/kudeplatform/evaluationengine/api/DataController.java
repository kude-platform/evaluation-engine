package com.github.kudeplatform.evaluationengine.api;

import com.github.kudeplatform.evaluationengine.domain.Dataset;
import com.github.kudeplatform.evaluationengine.mapper.DatasetMapper;
import com.github.kudeplatform.evaluationengine.mapper.DatasetMapperImpl;
import com.github.kudeplatform.evaluationengine.persistence.DatasetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author timo.buechert
 */
@RestController
@RequestMapping("/api/data")
@Slf4j
public class DataController {

    private final DatasetRepository datasetRepository;

    private final DatasetMapper datasetMapper;

    public DataController(final DatasetRepository datasetRepository) {
        this.datasetRepository = datasetRepository;
        this.datasetMapper = new DatasetMapperImpl();
    }


    @RequestMapping("/datasets")
    public List<Dataset> getDatasets() {
        return datasetRepository.findAll().stream().map(datasetMapper::toDomainObject).toList();
    }
}
