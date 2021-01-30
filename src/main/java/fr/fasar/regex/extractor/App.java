package fr.fasar.regex.extractor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import fr.fasar.regex.json.Mapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CommandLine.Command(name = "rextractor", mixinStandardHelpOptions = true, version = "1.0",
        description = "Extract the regex pattern in output.csv")
public class App implements Callable<Integer> {

    public static final String SEPARATOR = ";";
    @CommandLine.Parameters(index = "0", description = "The file to apply regex pattern.")
    private File file = null;

    @CommandLine.Option(names = {"-o", "--output"}, description = "output file")
    private File outputFile = new File("output.csv");
    @CommandLine.Option(names = {"-nf", "--not-found"}, description = "not found file")
    private File notFoundFile = new File("notfound.txt");


    @Override
    public Integer call() throws Exception {
        JsonNode config = getConfiguration();
        Charset charset = Charset.forName(config.get("charset").asText());
        // Create the Map of the extractors
        HashMap<String, List<Pattern>> extractors = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> extractI = config.get("extract").fields();
        while (extractI.hasNext()) {
            Map.Entry<String, JsonNode> next = extractI.next();
            List<Pattern> patterns = new ArrayList<>();
            extractors.put(next.getKey(), patterns);
            // Extract the patterns form the json.
            JsonNode value = next.getValue();
            ArrayNode array = (ArrayNode) value;
            for (int i = 0; i < array.size(); i++) {
                JsonNode jsonNode = array.get(i);
                String pattern = jsonNode.asText();
                Pattern compile = Pattern.compile(pattern);
                patterns.add(compile);
            }

        }
        // Extract fields needed to be extracted
        List<String> headers = new ArrayList<>();
        headers.addAll(extractors.keySet());
        StringBuilder sb = new StringBuilder();
        StringBuilder notFound = new StringBuilder();
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) {
                sb.append(SEPARATOR);
            }
            sb.append(headers.get(i));
        }
        sb.append("\n");

        List<String> lines = FileUtils.readLines(file, charset);
        for (String line : lines) {
            boolean isFullyExtracted = true;
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) {
                    sb.append(SEPARATOR);
                }
                List<Pattern> patterns = extractors.get(headers.get(i));
                String extracted = "";
                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        extracted = matcher.group(1);
                        break;
                    }
                }
                sb.append(extracted);
                if (StringUtils.isBlank(extracted)) {
                    isFullyExtracted = false;
                }
            }
            if (!isFullyExtracted) {
                notFound.append(line);
                notFound.append("\n");
            }
            sb.append("\n");
        }

        FileUtils.write(outputFile, sb.toString(), charset);
        FileUtils.write(notFoundFile, notFound.toString(), charset);

        return 0;
    }

    private JsonNode getConfiguration() throws java.io.IOException {
        File confFile = new File("config.json");
        JsonNode config;
        if (!confFile.exists()) {
            InputStream resourceAsStream = getResource("fr/fasar/regex/config.json");
            config = Mapper.mapper.readTree(resourceAsStream);
        } else {
            config = Mapper.mapper.readTree(confFile);
        }
        return config;
    }

    private InputStream getResource(String file) {
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(file);
        if (resourceAsStream != null) {
            return resourceAsStream;
        }
        resourceAsStream = App.class.getClassLoader().getResourceAsStream(file);
        if (resourceAsStream != null) {
            return resourceAsStream;
        }
        resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(file);
        if (resourceAsStream != null) {
            return resourceAsStream;
        }
        return null;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);

    }
}
