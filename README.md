# HugoIndexData

Used to build 2 JSON files which will support keyword searches in my Hugo blog. One index maps words to document identifiers; the other provides metadata for each of those identifiers (page name, title, etc). The indexer does not index every word. So, for example, stop words are removed, as well as words which are only numbers. Words with diacritics are folded to their ascii (non-diacritic) equivalent. The assumptionis that the search term is similarly folded prior to being used.

The content of each page is tokenized using a Lucene analyzer.

The resulting data is stored in two files:

page_index.json:

```json
{
  "0": {
    "date": "2013-11-15T19:39:03-04:00",
    "draft": "false",
    "title": "AdFind - the Command Line Active Directory Query Tool",
    "pageName": "adfind--the-command-line-active-directory-query-tool"
  },
  "1": {
    "date": "2019-12-07T14:08:00.000-05:00",
    "draft": "false",
    "title": "AWS Cognito - Signing Up and Signing In",
    "pageName": "aws-cognito-signing-up-and-signing-in"
  },
  "2": {
    "date": "2019-11-17T14:45:00.002-05:00",
    "draft": "false",
    "title": "Backup File Proliferation",
    "pageName": "backup-file-proliferation"
  },
  ...
  }
  ```
  
  word_index.json:
  
  ```json
  {
    "cdi": [
    27
  ],
  "require": [
    35,
    56
  ],
  "org.apache.lucene.search.highlight.textfragment": [
    35
  ],
  "click": [
    1,
    50,
    51,
    55
  ],
  ...
}
```
