package nl.gidsopenstandaarden.welldata.fhir.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import nl.gidsopenstandaarden.welldata.fhir.provider.ImplementationGuideResourceProvider;
import nl.gidsopenstandaarden.welldata.fhir.provider.StructureDefinitionResourceProvider;
import org.hl7.fhir.r4.model.ImplementationGuide;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Service to download and load FHIR Implementation Guide packages (.tgz).
 * Extracts StructureDefinition and ImplementationGuide resources from the package.
 */
@Service
public class IgPackageLoader {

    private static final Logger LOG = LoggerFactory.getLogger(IgPackageLoader.class);

    private final FhirContext fhirContext;
    private final IParser jsonParser;

    @Value("${welldata.ig.url:}")
    private String igPackageUrl;

    public IgPackageLoader() {
        this.fhirContext = FhirContext.forR4();
        this.jsonParser = fhirContext.newJsonParser();
    }

    /**
     * Load resources from the configured IG package URL into the providers.
     */
    public void loadIgPackage(
            StructureDefinitionResourceProvider structureDefinitionProvider,
            ImplementationGuideResourceProvider implementationGuideProvider) {

        if (igPackageUrl == null || igPackageUrl.isEmpty()) {
            LOG.info("No IG package URL configured (welldata.ig.url), skipping IG loading");
            return;
        }

        LOG.info("Loading IG package from: {}", igPackageUrl);

        try {
            byte[] packageData = downloadPackage(igPackageUrl);
            int sdCount = 0;
            int igCount = 0;

            try (TarArchiveInputStream tarIn = new TarArchiveInputStream(
                    new GZIPInputStream(new ByteArrayInputStream(packageData)))) {

                TarArchiveEntry entry;
                while ((entry = tarIn.getNextTarEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }

                    String name = entry.getName();

                    // Only process JSON files in the package root (not in example/ or other subdirs)
                    if (!name.endsWith(".json")) {
                        continue;
                    }

                    // Skip files in subdirectories like example/, xml/, other/
                    String relativePath = name.startsWith("package/") ? name.substring(8) : name;
                    if (relativePath.contains("/")) {
                        continue;
                    }

                    // Skip index and package.json files
                    if (relativePath.startsWith(".") || relativePath.equals("package.json")) {
                        continue;
                    }

                    String content = readEntryContent(tarIn, entry);

                    if (relativePath.startsWith("StructureDefinition-")) {
                        try {
                            StructureDefinition sd = jsonParser.parseResource(StructureDefinition.class, content);
                            structureDefinitionProvider.store(sd);
                            sdCount++;
                            LOG.debug("Loaded StructureDefinition: {}", sd.getId());
                        } catch (Exception e) {
                            LOG.warn("Failed to parse StructureDefinition from {}: {}", name, e.getMessage());
                        }
                    } else if (relativePath.startsWith("ImplementationGuide-")) {
                        try {
                            ImplementationGuide ig = jsonParser.parseResource(ImplementationGuide.class, content);
                            implementationGuideProvider.store(ig);
                            igCount++;
                            LOG.debug("Loaded ImplementationGuide: {}", ig.getId());
                        } catch (Exception e) {
                            LOG.warn("Failed to parse ImplementationGuide from {}: {}", name, e.getMessage());
                        }
                    }
                }
            }

            LOG.info("Loaded {} StructureDefinitions, {} ImplementationGuides from IG package", sdCount, igCount);

        } catch (Exception e) {
            LOG.error("Failed to load IG package from {}: {}", igPackageUrl, e.getMessage(), e);
        }
    }

    private byte[] downloadPackage(String url) throws Exception {
        LOG.debug("Downloading package from: {}", url);

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to download package: HTTP " + response.statusCode());
        }

        LOG.debug("Downloaded {} bytes", response.body().length);
        return response.body();
    }

    private String readEntryContent(TarArchiveInputStream tarIn, TarArchiveEntry entry) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        long remaining = entry.getSize();

        while (remaining > 0 && (len = tarIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
            baos.write(buffer, 0, len);
            remaining -= len;
        }

        return baos.toString(StandardCharsets.UTF_8);
    }
}
