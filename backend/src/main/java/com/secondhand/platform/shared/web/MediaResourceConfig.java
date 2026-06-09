package com.secondhand.platform.shared.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class MediaResourceConfig implements WebMvcConfigurer {
    private static final String[] PUBLIC_UPLOAD_RESOURCE_PATTERNS = {
            "/uploads/product-image/**",
            "/uploads/community-image/**",
            "/uploads/avatar/**",
            "/uploads/home/**"
    };

    private final Path storageRoot;

    public MediaResourceConfig(@Value("${media.storage-root:}") String storageRoot) {
        this.storageRoot = resolveStorageRoot(storageRoot);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        addPublicResourceHandler(registry, "/uploads/product-image/**", "uploads/product-image");
        addPublicResourceHandler(registry, "/uploads/community-image/**", "uploads/community-image");
        addPublicResourceHandler(registry, "/uploads/avatar/**", "uploads/avatar");
        addPublicResourceHandler(registry, "/uploads/home/**", "uploads/home");
    }

    Path uploadsRoot() {
        return storageRoot.resolve("uploads").normalize();
    }

    String[] publicUploadResourcePatterns() {
        return PUBLIC_UPLOAD_RESOURCE_PATTERNS.clone();
    }

    private void addPublicResourceHandler(ResourceHandlerRegistry registry, String pattern, String relativeLocation) {
        registry.addResourceHandler(pattern)
                .addResourceLocations(storageRoot.resolve(relativeLocation).normalize().toUri().toString())
                .setCachePeriod(3600)
                .resourceChain(true)
                .addResolver(new PublicUploadPathResourceResolver());
    }

    private Path resolveStorageRoot(String configuredRoot) {
        if (configuredRoot == null || configuredRoot.isBlank()) {
            throw new IllegalStateException("media.storage-root required");
        }
        return Path.of(configuredRoot).toAbsolutePath().normalize();
    }

    static final class PublicUploadPathResourceResolver extends PathResourceResolver {
        @Override
        public Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource resource = super.getResource(resourcePath, location);
            if (resource == null) {
                return null;
            }
            if (!isRegularFileInside(resource, location)) {
                return null;
            }
            return resource;
        }

        private boolean isRegularFileInside(Resource resource, Resource location) {
            try {
                Path locationRoot = Path.of(location.getURI()).toAbsolutePath().normalize().toRealPath();
                Path target = Path.of(resource.getURI()).toAbsolutePath().normalize();
                if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                    return false;
                }
                return target.toRealPath().startsWith(locationRoot);
            } catch (Exception ignored) {
                return false;
            }
        }
    }
}
