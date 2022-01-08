## Academic Integrity

This account does not take responsibility for any sorts of plagiarism about the repositories contents. And discourages any action that goes againts the rules of academic integrity.

## Note

```
 All of the HTTP layer request are made using only the java socket API. 
 Rest of the protocol implementation are custom made   
```

## File-Dowloader-ASYNC

This is a multi threaded version of FileDowloader and uses java Futures API to do a similar thing the the single threaded.

## How to use it ?


- Compiling
  - javac FileDowlader.java
  
- Test Cases
  - http://www.cs.bilkent.edu.tr/~cs421/fall21/project1/index1.txt
  - http://www.cs.bilkent.edu.tr/~cs421/fall21/project1/index2.txt

- Running
  - java ParallelFileDownloader <One of the test cases> <number of threads to use> 
  - java ParallelFileDownloader www.cs.bilkent.edu.tr/~cs421/fall21/project1/index2.txt 5


