/*
 * qualinsight-plugins-sonarqube-badges
 * Copyright (c) 2015-2016, QualInsight
 * http://www.qualinsight.com/
 *
 * This program is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program. If not, you can retrieve a copy
 * from <http://www.gnu.org/licenses/>.
 */
package com.qualinsight.plugins.sonarqube.badges.ws.measure;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.server.ServerSide;
import com.qualinsight.plugins.sonarqube.badges.ws.SVGImageColor;
import com.qualinsight.plugins.sonarqube.badges.ws.SVGImageData;
import com.qualinsight.plugins.sonarqube.badges.ws.SVGImageGenerator;
import com.qualinsight.plugins.sonarqube.badges.ws.SVGImageMinimizer;
import com.qualinsight.plugins.sonarqube.badges.ws.SVGImageTemplate;

/**
 * Generates SVG badge based on a measure value. A reusable {@link InputStream} is kept in a cache for each generated image in order to decrease computation time.
 *
 * @author Michel Pawlak
 */
@ServerSide
public final class MeasureBadgeGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeasureBadgeGenerator.class);

    private final Map<SVGImageTemplate, Map<MeasureHolder, InputStream>> measureBadgesMap = new EnumMap<>(SVGImageTemplate.class);

    private final Map<SVGImageTemplate, Map<MeasureHolder, InputStream>> measureBlinkingBadgesMap = new EnumMap<>(SVGImageTemplate.class);

    private SVGImageGenerator imageGenerator;

    private SVGImageMinimizer minimizer;

    /**
     * {@link MeasureBadgeGenerator} IoC constructor.
     *
     * @param imageGenerator {@link SVGImageGenerator} service to be used.
     * @param fontReplacer {@link SVGImageMinimizer} service to be used.
     */
    public MeasureBadgeGenerator(final SVGImageGenerator imageGenerator, final SVGImageMinimizer fontReplacer) {
        this.imageGenerator = imageGenerator;
        this.minimizer = fontReplacer;
        for (final SVGImageTemplate template : SVGImageTemplate.values()) {
            this.measureBadgesMap.put(template, new HashMap<MeasureHolder, InputStream>());
            this.measureBlinkingBadgesMap.put(template, new HashMap<MeasureHolder, InputStream>());
        }
        LOGGER.info("MeasureBadgeGenerator is now ready.");
    }

    /**
     * Returns an {@link InputStream} holding the content of the generated image for the provided {@link MeasureHolder}. All {@link InputStream}s are cached for future reuse.
     *
     * @param measureHolder measure for which the image has to be generated
     * @param template {@link SVGImageTemplate} to be used
     * @param blinkingValueBackgroundColor true if the badge must be blinking in case of quality gate error
     * @return {@link InputStream} holding the expected SVG image
     * @throws IOException if a IO problem occurs during streams manipulation
     */
    public InputStream svgImageInputStreamFor(final MeasureHolder measureHolder, final SVGImageTemplate template, final boolean blinkingValueBackgroundColor) throws IOException {
        InputStream svgImageRawInputStream;
        InputStream svgImageTransformedInputStream;
        Map<SVGImageTemplate, Map<MeasureHolder, InputStream>> workingMap;
        if (blinkingValueBackgroundColor) {
            workingMap = this.measureBadgesMap;
        } else {
            workingMap = this.measureBlinkingBadgesMap;
        }
        if (workingMap.containsKey(measureHolder)) {
            LOGGER.debug("Found SVG image for measure holder in cache, reusing it.");
            svgImageTransformedInputStream = workingMap.get(template)
                .get(measureHolder);
            // we don't trust previous InpuStream user, so we reset the position of the InpuStream
            svgImageTransformedInputStream.reset();
        } else {
            LOGGER.debug("Generating SVG image for measure holder, then caching it.");
            final SVGImageData data = SVGImageData.Builder.instance(this.imageGenerator.fontProvider())
                .withTemplate(template)
                .withLabelText(measureHolder.metricName())
                .withLabelBackgroundColor(SVGImageColor.DARK_GRAY)
                .withValueText(measureHolder.value())
                .withValueBackgroundColor(measureHolder.backgroundColor())
                .build();
            svgImageRawInputStream = this.imageGenerator.generateFor(data);
            // set parameters
            final Map<String, Object> parameters = ImmutableMap.<String, Object> builder()
                .put("IS_BLINKING_BADGE", Boolean.toString(blinkingValueBackgroundColor && measureHolder.backgroundColor()
                    .equals(SVGImageColor.RED)))
                .build();
            // minimze SVG stream
            svgImageTransformedInputStream = this.minimizer.process(svgImageRawInputStream, parameters);
            // mark svgImageInputStream position to make it reusable
            svgImageTransformedInputStream.mark(Integer.MAX_VALUE);
            // put it into cache
            workingMap.get(template)
                .put(measureHolder, svgImageTransformedInputStream);
        }
        return svgImageTransformedInputStream;
    }
}
