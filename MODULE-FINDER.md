# Finder module port

Added `dev.zcore.modules.render.Finder` from the uploaded Meteor addon module.

Integration changes:
- Package changed from `com.larp.debug.modules` to `dev.zcore.modules.render`.
- Category changed from `AddonTemplate.CATEGORY` to `ZCoreCat.UTILITY`.
- Registered in `dev.zcore.ZCore` with `Modules.get().add(new Finder());`.

The detection/render logic from the original module is kept intact as much as possible.
