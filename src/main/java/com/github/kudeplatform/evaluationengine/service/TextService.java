package com.github.kudeplatform.evaluationengine.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * @author timo.buechert
 */
@Service
public class TextService {

    public static final String TEXT_PROPERTIES = "Text";

    public static final String DEFAULT_MESSAGE = "messages.unknownerror";

    private final ResourceBundle resourceBundle;

    public TextService() {
        this.resourceBundle = ResourceBundle.getBundle(TEXT_PROPERTIES, Locale.US);
    }

    public String getText(final String key) {
        if (this.resourceBundle.containsKey(key)) {
            return this.resourceBundle.getString(key);
        }

        return this.resourceBundle.getString(DEFAULT_MESSAGE);
    }

}
