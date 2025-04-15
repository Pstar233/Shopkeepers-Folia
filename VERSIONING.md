# Versioning scheme

The versioning scheme used by this software is similar to Semantic Versioning (https://semver.org/), with the following differences:
* In addition to ```Major``` for **backwards incompatible changes** (regarding API and behavior), we have an ```Epoch``` component at the beginning, which can be used to denote **especially significant** backwards incompatible changes (e.g. a complete rewrite of the software, or to emphasize the effort or impact of an update). The ```Epoch``` component might not get incremented very often, or may for some software even stay the same during its complete lifetime.
* We don't differentiate between the ```Minor``` and the ``` Patch``` components: We use a single ```Minor``` component for all types of **backwards compatible changes** (e.g. feature additions, deprecations, bug fixes, corrected documentation).

Just like in Semantic Versioning, we can:
* Denote **Pre-Releases** (e.g. 1.0.0-alpha, 1.0.0-beta.3). During pre-releases anything may change at any time: The public API should not be considered stable.
* Append additional build metadata (e.g. build numbers, build dates, commit hashes).

**The overall version format looks like this:** ```Epoch.Major.Minor[-pre.id][+metadata]```

Additional notes on our versioning scheme:
* The formatting and precedence rules are the same as defined by Semantic Versioning.
* We may omit trailing components consisting of zeros (1.9.0 = 1.9, 1.9.0-alpha.0 = 1.9-alpha.0 = 1.9-alpha).
* Alternatively to pre-release tags, an ```Epoch``` of 0 may be used to denote the initial development phase of the project. During this phase the constraints for the ```Major``` and ```Minor``` components are loosened and their meaning might be defined differently. Anything may change at any time: The public API should not be considered stable.
* Versions are usually only assigned for (pre-)releases. Each new release should come with at least an increment of the ```Minor``` component. Individual commits and development builds might not get individual versions assigned (they might only differ in their build metadata, e.g. build number, date, etc.).
