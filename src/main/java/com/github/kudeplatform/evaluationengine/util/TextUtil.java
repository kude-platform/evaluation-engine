package com.github.kudeplatform.evaluationengine.util;

import com.github.kudeplatform.evaluationengine.domain.Repository;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TextUtil {

    public static List<Repository> parseRepositoriesFromMassInput(final String massInput, final String username, final String token) {
        final List<Repository> repositories = new ArrayList<>();

        final String[] lines = massInput.split("\n");
        for (final String line : lines) {
            final String[] parts = line.split(";");
            if (parts.length == 2 && parts[0].trim().startsWith("http")) {
                final String repositoryUrl = parts[0].trim();
                final String name = parts[1].trim();
                repositories.add(new Repository(name, repositoryUrl, username, token));
            }
        }

        return repositories;
    }
}
