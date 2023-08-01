package org.ajames.hugoindexdata;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class IndexerTest {

    public IndexerTest() {
    }

    @Test
    public void testMain() throws Exception {
        System.out.println("Testing main.");
        String[] args = new String[1];
        args[0] = "data/";
        Indexer.main(args);

        long mismatch;

        System.out.println("Testing page index file.");
        mismatch = Files.mismatch(
                Path.of(args[0] + "content/static/page_index.json"),
                Path.of(args[0] + "EXPECTED_page_index.json")
        );
        if (mismatch > -1L) {
            System.out.println("First mismatch in page index data at position " + mismatch);
        }
        assertTrue(mismatch == -1L);

        System.out.println("Testing word index file.");
        mismatch = Files.mismatch(
                Path.of(args[0] + "content/static/word_index.json"),
                Path.of(args[0] + "EXPECTED_word_index.json")
        );
        if (mismatch > -1L) {
            System.out.println("First mismatch in word index data at position " + mismatch);
        }
        assertTrue(mismatch == -1L);
    }

}
