# Slightly Java Servlet

- **Author**: Valter Nepomuceno
- **When**: June, 2017
- **Based in**: Lisbon, Portugal
- **Current Job**: Software Developer
- **Email Address**: valter.nep@gmail.com
- **Reference Links**: [LinkedIn](https://pt.linkedin.com/in/valternepomuceno) | [GitHub](https://github.com/Vnepomuceno) | [Facebook](https://www.facebook.com/valter.nepomuceno)


## Description

Slightly is a Java servlet that responds to requests for HTML template files. The servlet reads the requested file, parses the HTML, and processes the server-side javascript and server-side data-attributes, and finally sends the resulting HTML to the browser.

To implement functionality required for the Slightly servlet, the following steps were required to return the appropriate response:

1. Load the requested HTML document file.
2. Evaluate Javascript code in the HTML document using Nashorn Javascript engine.
3. Evaluate 'data-if' expressions using Javascript engine.
4. Evaluate 'data-for-x' expressions using Javascript engine.
5. Evaluate $-expressions
6. Print out resulting response to browser.

In case an exception is thrown during execution, the error message is printed out in the reponse returned to the browser.

## Running Servlet

To run the servlet, execute the following Maven command in the command line:

```maven
mvn jetty:run
```

You should be getting something similar to the following output:
![alt text](http://i68.tinypic.com/ju8h2u.png)
And the application should be available at [http://localhost:8080](http://localhost:8080).

## Libraries & Tools

When solving this solution, the following libraries and programming were used:

|Dependency|Version|Description|
|----|-------:|-----|
|Maven|3.6.0|Dependency Management System|
|Jetty|9.4.0|Servlet Engine|
|JSoup|1.10.3|HTML Parser|
|Nashorn|8.0|Javascript Engine|
|Apache Common Lang|3.5|Utilities Library|
|Intellij IDEA|2017.1|Integrated Development Environment|
|Git|2.11.0|Version Control System|