Play Income Sheet Processor
===============
Fork of https://code.google.com/p/play-income-sheet-processor/

Changes to the original source code:
+ gradle build system


Description
===============
Aimed for Polish developers (since it uses official Polish exchange provider) command line utility that process income reports from Google Play Store and generates Excel document with transaction list, pivot table with per-day per-currency income and quote difference between official NBP exchange quotes and one used by Google. It also collects VAT related information and other data. 

Data can be provided locally or fetched directly from Google Cloud Storage bucket through Google GCS API (either using user credential through OAuth2 or using authorized service, see below for details).


Usage 
===============
Assuming application was compiled to runnable jar named isp.jar
```
java -jar isp.jar [OPTIONS]...
```

**OPTIONS**

extended help
```
-h,--help
```
output file name (optional, by default yyyyMM.xlsx, where date is report date
```
-o,--output <arg>
```
prevents output file overwrite
```
--no-overwrite
```
properties file with application configuration (use -h for config template)
```
-C,--config <arg>
```
raport date in format yyyy.MM or yyyyMM, only used if local-dir is not defined (optional, by default previous month is used)
```
-D,--date <arg>
```
use local files from specified directory instead of GCS
```
-L,--local-dir <arg>
```
GCS bucked for Play reports
```
--gcs-bucket <arg>
```
Google API client secret json file
```
--gcs-client-secret <arg>
```
Google API service account access cert PK12 file
```
--gcs-service-cert <arg>
```
Google API service account e-mail address
```
--gcs-service-email <arg>
```
disables VAT data processing (sales reports will not be used, implies no-vat-sheet)
```
--no-vat
```
enables processing of tax deduction report, currently there are two reports generated per month, transactions and tax deduction (for Brazil, possibly other countries), yet this data seems to be included in main report as well hence is ignored by default
```
--process-tax-reports
```
disables summary sheet output
```
--no-summary-sheet
```
disables vat sheet output
```
--no-vat-sheet
```
disables xchange sheet output (no xchange data will be fetched)
```
--no-xchange-sheet
```
verbose debug messages
```
-v,--verbose
```


GCS configration
===============
To use GCS fetch Google API access has to be configured, there are two possible method:

User credentials using oauth2 and client secret - to do so client secret json file has to be provided (see here form more information: setting oauth2 for Google API). This method requires manual operation to obtain access token (Google account login page will be opened in default browser).

Service access - with private key and service e-mail (see here for more info: setting service account for Google API). This is fully automated (ie. no need for manual login) but generated service e-mail address has to be authorized in Google Play developer console for financial data access


Notes
===============
In local mode (-L switch) with vat processing enabled two sales report are required: sheet for report month and sheet from month before (in GCS mode they are fetched automatically). Sales reports are created using transaction date and earning report are created using billing (charge) date which usually happens few hours after purchase therefore in to match all transaction from earnings reports old sales report has to be provided.


License
===============

    Copyright 2015 bjanusz@bytestorm.pl
    Copyright 2015 Tomasz Rozbicki

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
