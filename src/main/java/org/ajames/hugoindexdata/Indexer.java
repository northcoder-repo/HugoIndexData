package org.ajames.hugoindexdata;

import com.google.gson.Gson;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/*
  Used to build 2 JSON files which will support keyword searches in
  my Hugo blog. One index maps words to document identifiers; the other
  provides metadata for each of those identifiers (page name, title, etc).
  The indexer does not index every word. So, for example, stop words are
  removed, as well as words which are only numbers. Words with diacritics
  are folded to their ascii (non-diacritic) equivalent. The assumption
  is that the search term is similarly folded prior to being used.
 */
public class Indexer {

    private static final Logger LOGGER = Logger.getLogger(Indexer.class.getName());
    private static String ROOT_DIR;

    private final Map<String, Set<Integer>> wordIndex = new HashMap<>();
    private final Map<Integer, Map<String, String>> pageIndex = new HashMap<>();
    private final Map<String, Integer> reverseIndex = new HashMap<>();

    public static void main(String[] args) throws IOException {
        ROOT_DIR = args[0];
        Indexer indexer = new Indexer();
        indexer.doWork();
    }

    private void doWork() throws IOException {
        PageFiles pageFiles = new PageFiles();
        Files.walkFileTree(Path.of(ROOT_DIR + "content/post/"), pageFiles);
        Gson gson = new Gson();
        try (FileWriter writer = new FileWriter(ROOT_DIR + "content/static/word_index.json")) {
            gson.toJson(wordIndex, writer);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error!", e);
        }
        try (FileWriter writer = new FileWriter(ROOT_DIR + "content/static/page_index.json")) {
            gson.toJson(pageIndex, writer);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error!", e);
        }
    }

    private String getContent(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private String transform(String input) throws IOException {

        // the stopwords file is in the default package on the classpath:
        Map<String, String> stopMap = new HashMap<>();
        stopMap.put("words", "stopwords.txt");
        stopMap.put("format", "wordset");

        Analyzer analyzer = CustomAnalyzer.builder()
                .withTokenizer("icu")
                .addTokenFilter("lowercase")
                .addTokenFilter("icuFolding")
                .addTokenFilter("stop", stopMap)
                .build();

        TokenStream ts = analyzer.tokenStream("myField", new StringReader(input));
        CharTermAttribute charTermAtt = ts.addAttribute(CharTermAttribute.class);

        StringBuilder sb = new StringBuilder();
        try {
            ts.reset();
            while (ts.incrementToken()) {
                sb.append(charTermAtt.toString()).append(" ");
            }
            ts.end();
        } finally {
            ts.close();
        }
        return sb.toString().trim();
    }

    private void processWord(String word, int pageId) {
        if (include(word)) {
            if (wordIndex.containsKey(word)) {
                wordIndex.get(word).add(pageId);
            } else {
                Set<Integer> pages = new HashSet<>();
                pages.add(pageId);
                wordIndex.put(word, pages);
            }
        }
    }

    private boolean include(String word) {
        if (word.length() < 3 && !word.toLowerCase().equals("h2")) {
            return false;
        }
        return !isNumeric(word);
    }

    private boolean isNumeric(String str) {
        //match a number with optional '-' and decimal.
        return str.matches("-?\\d+(\\.\\d+)?");
    }

    private void buildWordIndex(Path file, String pageName) throws IOException {
        String content = getContent(file);
        Map<String, String> metadata = getMetadata(content, pageName);

        buildPageIndices(metadata);

        String words = transform(content);
        for (String word : words.split(" ")) {
            processWord(word, reverseIndex.get(pageName));
        }
    }

    private void buildPageIndices(Map<String, String> metadata) {
        String pageName = metadata.get("pageName");
        if (!reverseIndex.containsKey(pageName)) {
            Integer i = reverseIndex.size();
            reverseIndex.put(pageName, i);
            pageIndex.put(i, metadata);
        }
    }

    private Map<String, String> getMetadata(String content, String pageName) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("pageName", pageName);

        String frontMatter = "";
        try {
            frontMatter = content.split("(?m)^--- *$", 3)[1]; //?m = multiline
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error locating front matter for {0}", ex);
        }
        String[] entries = frontMatter.split("(\\r\\n|\\r|\\n)"); // newline variations
        for (String entry : entries) {
            String[] keyVal = entry.split(":", 2);
            if (keyVal.length == 2 && !keyVal[0].isBlank() && !keyVal[1].isBlank()) {
                String key = keyVal[0].trim();
                String value = removeSurroundingQuotes(keyVal[1].trim());
                metadata = addToMeta(key, value, metadata);
            }
        }
        checkMetadata(metadata);
        return metadata;
    }

    private String removeSurroundingQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        } else {
            return value;
        }
    }

    private Map<String, String> addToMeta(String key, String value, Map<String, String> metadata) {
        if (key.equals("title") || key.equals("date") || key.equals("draft")) {
            metadata.put(key, value);
        }
        return metadata;
    }

    private void checkMetadata(Map<String, String> metadata) {
        if (metadata.size() != 4) {
            LOGGER.log(Level.WARNING, "Incomplete metadata set found: {0}", metadata.toString());
        }
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (entry.getValue().isBlank()) {
                LOGGER.log(Level.WARNING, "Blank metadata value found in: {0}", metadata.toString());
            }
            if (entry.getKey().equals("draft") && !entry.getValue().equals("true")
                    && !entry.getValue().equals("false")) {
                LOGGER.log(Level.WARNING, "Invalid \"draft\" value found in: {0}", metadata.toString());
            }
            if (entry.getKey().equals("date")) {
                try {
                    ZonedDateTime.parse(entry.getValue());
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Invalid \"date\" value found in: {0}", metadata.toString());
                }
            }
        }
    }

    class PageFiles extends SimpleFileVisitor<Path> {

        @Override
        public FileVisitResult visitFile(Path file,
                BasicFileAttributes attr) throws IOException {
            if (attr.isRegularFile()) {
                String fileName = fileName(file);
                if (extension(fileName).equals("md")) {
                    String pageName = pageName(fileName);
                    buildWordIndex(file, pageName);
                }
            }
            return FileVisitResult.CONTINUE;
        }

        private String fileName(Path file) {
            return file.getFileName().toString();
        }

        private String extension(String fileName) {
            String extension = "";
            int i = fileName.lastIndexOf(".");
            if (i > 0) {
                extension = fileName.substring(i + 1);
            }
            return extension;
        }

        private String pageName(String fileName) {
            return fileName.substring(0, fileName.length() - 3);
        }
    }

}
