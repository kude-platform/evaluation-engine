package com.github.kudeplatform.evaluationengine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author timo.buechert
 */
@Service
@RequiredArgsConstructor
public class HintsService {

    private static final Map<String, String> HINTS = Map.of(
            "NULL_POINTER_EXCEPTION", "Check if you are trying to access a null object.",
            "ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION", "Check if you are trying to access an index that is out of bounds.",
            "CLASS_CAST_EXCEPTION", "Check if you are trying to cast an object to a class that it is not an instance of.",
            "CONNECTION_PROBLEM", "There seems to be a connection problem between the nodes.",
            "MVN_BUILD_FAILED", "The Maven build failed. This might be due to incorrect folder structure or missing dependencies."
    );

    public String getHintForCategory(final String category) {
        return HINTS.get(category);
    }

}
