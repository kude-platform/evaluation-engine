package com.github.kudeplatform.evaluationengine.api;

import com.github.kudeplatform.evaluationengine.domain.Dataset;
import com.github.kudeplatform.evaluationengine.mapper.DatasetMapper;
import com.github.kudeplatform.evaluationengine.mapper.DatasetMapperImpl;
import com.github.kudeplatform.evaluationengine.persistence.DatasetRepository;
import com.github.kudeplatform.evaluationengine.view.NotifiableComponent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.HashMap;
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

    final List<NotifiableComponent> activeViewComponents;

    @Getter
    private final HashMap<String, String> lastUpdatedByNode = new HashMap<>();

    public DataController(final DatasetRepository datasetRepository, final List<NotifiableComponent> activeViewComponents) {
        this.datasetRepository = datasetRepository;
        this.activeViewComponents = activeViewComponents;
        this.datasetMapper = new DatasetMapperImpl();
    }


    @RequestMapping("/datasets")
    public List<Dataset> getDatasets(final HttpServletRequest request) {
        this.lastUpdatedByNode.put(request.getRemoteAddr(), ZonedDateTime.now().toString());
        return datasetRepository.findAll().stream().map(datasetMapper::toDomainObject).toList();
    }

}
