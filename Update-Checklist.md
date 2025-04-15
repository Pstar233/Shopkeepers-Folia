# Update checklist

## Mappings-only update

I.e. the mappings version changed without there being a bump to the CraftBukkit version.
Since our modules can currently only build against specific CraftBukkit versions, but not mappings versions, we are currently only able to support the latest mappings revision for each CraftBukkit version.

To support the latest mappings version:
* Update the mappings version of the corresponding CompatVersion in NMSManager.
* Make sure that Gradle resolves the latest CraftBukkit snapshot version (caching might prevent this).
  * However, there are test cases that should catch if we attempt to build against an unexpected mappings version.
* If necessary, update the current compat module code for the CraftBukkit version.
* Rebuild the plugin. This should rebuild the compat module against the latest version of CraftBukkit with the latest mappings version.

## Minecraft update

* Add a new CompatVersion entry in NMSManager.
	* Increment the revision number of the compat version (behind the 'R'). Note that for some minor Minecraft updates this version may not necessarily align with CraftBukkit's 'Minecraft Version'.

* Add a new module (subproject) for the new compat version:
	* Copy an existing module and rename module and package folders.
	* Update the CraftBukkit version inside the 'build.gradle' file of the new module.
	* Add an entry for the module in the root 'settings.gradle' file.
	* Add an entry for the module in the 'build.gradle' file of the 'dist' module.
	* Update the NMSHandler class:
		* Package name.
		* Output of #getVersionId. This should match the compat version (not necessarily CraftBukkit's Minecraft version).
		* Update all NMS version specific imports and references.
		* Check all NMS version specific code:
			* Methods or fields might no longer exist or might have been renamed.
	* Also update the package names of all NMS module test classes.

* Add a new build entry in 'scripts/installSpigotDependencies.sh'.

* If Spigot requires a new JDK version to build:
	* Define a version alias for the JDK version in 'scripts/installJDK.sh'.
	* Add a JDK installation entry in 'scripts/installSpigotDependencies.sh'.
	* Update the JDK installation entry in 'build.sh'.
	* Update the JDK version inside the GitHub workflow '.github/workflows/build.yml'.

* New mobs:
	* Test if they can be used for shopkeepers.
	* Add a note about potential issues in SKLivingShopObjectTypes.
	* If there are no severe issues, add them to the by default enabled living shop types.
	* Check which equipment slots are supported and adjust EquipmentUtils and
	  SKLivingShopObject#getEditableEquipmentSlots.

* New features for new or existing shop objects (mobs, signs, etc.):
	* If not yet existing: Add a new shop object type and register it inside SKLivingShopObjectTypes.
	* Add new editor buttons and messages to the editor menu of the shop object.

* New blocks or items:
	* Check the ItemUtils if there are any material lists or mappings that need to be updated.
		* Containers, chests, shulker boxes, signs, rails.
		* Wool material by dye color, carpet material by dye color.
	* Also check if the supported containers in ShopContainers require changes.

* New enchantments:
	* Check the aliases inside EnchantmentUtils.

* New potion types or items with potion data:
	* Check the aliases and the parsing inside PotionUtils.

* New MerchantRecipe properties:
	* Update the comparator in MerchantUtils.
	* Check the MerchantRecipe constructions in MerchantUtils.

* New explosion result enum values:
	* Check FailedHandler and NMSHandlers and map destroying explosion results correctly.

* If there are major differences, consider dropping support for older Minecraft versions.
	* Remove the corresponding modules:
		* Folders.
		* Entries in root 'settings.gradle'.
		* Entries in 'modules/dist/build.gradle'.
		* CompatVersion entries in NMSManager class.
		* Entries in 'scripts/installSpigotDependencies.sh' script.
	* Update the minimal Bukkit/Spigot/CraftBukkit dependency versions inside the 'gradle/libs.versions.toml' file.
	* Update the 'api-version' inside the 'plugin.yml' file.
	* Update the Minecraft version specific test code inside the 'main' module. The test cases and the default config might need to be updated (e.g. if there have been changes to Bukkit's item serialization).
	* Update the code base (optional):
		* Check for legacy data migrations that could be removed now.
		* Check if there are new Bukkit features that can replace portions of the existing NMS specific code.
		* Use the EntityType enum to get the name of default enabled mobs inside the Settings.
		* Check for TODO notes that mention Bukkit version dependencies.

## On every update

* Build and test the new version.
* Make sure the changelog is complete. Fill in the release date.
* Update the version in the root 'gradle.properties' file (remove the '-SNAPSHOT' tag).
* Commit, build and deploy.
* Add a new git tag for that version.
* Update the version in the root 'gradle.properties' file for working on the next version. Re-add the '-SNAPSHOT' tag.
* If not yet done, add a new entry inside the changelog for the next version.
* Commit.

## Update documentation:

* Update the wiki, depending on the changes of the update:
	* Default config.
	* Permissions page.
	* Commands page.
	* Shop setup pages.
	* Images of the default shopkeeper editor.
	* 'Known issues' page.

* Update the project pages, if required: dev.bukkit.org, spigotmc.org

* If there have been message changes, update the language files repository:
	* Add a new branch for the new version.
	* Update the contents of the default language files, and delete all other language files that are now outdated.
	* Update the changelog of the language files repository.
	* Change the default branch of the repository online.

## Upload:

* Write changelog: Copy previous changelog, replace contents, adjust the formatting.
* Upload to dev.bukkit.org.
	* Post a comment about the update.
* Upload to spigotmc.org.

