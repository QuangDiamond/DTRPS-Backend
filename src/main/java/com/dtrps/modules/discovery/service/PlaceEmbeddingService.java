
package com.dtrps.modules.discovery.service;

import com.dtrps.modules.discovery.config.OpenAiProperties;
import com.dtrps.modules.discovery.entity.PlaceEmbedding;
import com.dtrps.modules.discovery.repository.EmbeddingPlaceRepository;
import com.dtrps.modules.discovery.repository.PlaceEmbeddingRepository;
import com.dtrps.modules.poi.entity.Place;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PlaceEmbeddingService {

    private static final int BATCH_SIZE = 100;

    private final EmbeddingPlaceRepository placeRepository;
    private final PlaceEmbeddingRepository embeddingRepository;
    private final PlaceEmbeddingTextBuilder textBuilder;
    private final EmbeddingGateway gateway;
    private final OpenAiProperties properties;
    private final TransactionTemplate transactionTemplate;

    public PlaceEmbeddingService(
            EmbeddingPlaceRepository placeRepository,
            PlaceEmbeddingRepository embeddingRepository,
            PlaceEmbeddingTextBuilder textBuilder,
            EmbeddingGateway gateway,
            OpenAiProperties properties,
            TransactionTemplate transactionTemplate) {

        this.placeRepository = placeRepository;
        this.embeddingRepository = embeddingRepository;
        this.textBuilder = textBuilder;
        this.gateway = gateway;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    public RebuildResult rebuild(int limit) {

    if (limit < 1 || limit > 500) {
        throw new IllegalArgumentException(
            "Limit phải nằm trong khoảng 1 đến 500"
        );
    }

        String model = properties.resolvedModel();

        List<Place> places =
            placeRepository.findActiveWithCategories();

        Map<Long, PlaceEmbedding> existing =
            embeddingRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                    e -> e.getPlace().getPlaceId(),
                    Function.identity()
                ));

        List<PendingEmbedding> pending = new ArrayList<>();

        int skipped = 0;
        int failed = 0;

        for (Place place : places) {

            if (place.getDescription() == null
                    || place.getDescription().isBlank()) {
                failed++;
                continue;
            }

            String sourceText = textBuilder.build(place);
            String hash = sha256(model + "\n" + sourceText);

            PlaceEmbedding old =
                existing.get(place.getPlaceId());

            if (old != null
                    && Objects.equals(old.getModel(), model)
                    && Objects.equals(old.getSourceHash(), hash)
                    && old.getEmbedding() != null) {

                skipped++;
                continue;
            }

            pending.add(new PendingEmbedding(
                place,
                sourceText,
                hash
            ));
            if (pending.size() >= limit) {
    break;
}
        }

        int embedded = 0;

        for (int start = 0;
                start < pending.size();
                start += BATCH_SIZE) {

            List<PendingEmbedding> batch =
                pending.subList(
                    start,
                    Math.min(start + BATCH_SIZE, pending.size())
                );

            List<String> texts = batch.stream()
                .map(PendingEmbedding::sourceText)
                .toList();

            // Gọi OpenAI ngoài transaction database.
            List<float[]> vectors = gateway.embedAll(texts);

            // Chỉ mở transaction lúc lưu kết quả.
            transactionTemplate.executeWithoutResult(status -> {

                List<PlaceEmbedding> records =
                    new ArrayList<>();

                for (int i = 0; i < batch.size(); i++) {

                    PendingEmbedding item = batch.get(i);

                    Long placeId =
                        item.place().getPlaceId();

                    PlaceEmbedding record =
                        existing.get(placeId);

                    if (record == null) {
                        record = new PlaceEmbedding();
                        record.setPlace(item.place());
                    }

                    record.setEmbedding(vectors.get(i));
                    record.setSourceText(item.sourceText());
                    record.setSourceHash(item.sourceHash());
                    record.setModel(model);

                    records.add(record);
                }

                embeddingRepository.saveAll(records);
                embeddingRepository.flush();
            });

            embedded += batch.size();
        }

        return new RebuildResult(embedded, skipped, failed);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

            byte[] bytes = digest.digest(
                value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(bytes);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                "SHA-256 không khả dụng",
                e
            );
        }
    }

    private record PendingEmbedding(
        Place place,
        String sourceText,
        String sourceHash
    ) {}

    public record RebuildResult(
        int embedded,
        int skipped,
        int failed
    ) {}
}
