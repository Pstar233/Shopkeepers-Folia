# Contributing to Shopkeepers

### Pull Requests

Keep them small and on point. Prefer creating multiple small pull requests instead of few monolithic ones. The easier they are to review and the less they affect existing code and behavior, the likelier it is that they get accepted.

For any major changes or additions, please create a ticket first and describe your intentions, roughly how you plan to implement them, and how your changes might affect existing code and behavior (what are possible issues / risks). So that they can be discussed before you spent countless hours implementing them, only for them to get rejected in the end.

### Volatile code

All "volatile" code (any code that relies on CraftBukkit, NMS, or specific Bukkit versions) should be in the `compat` package. Please keep this code to a minimum wherever possible, as adding more volatile code makes the updating process more difficult. If it is possible to create a non-volatile fallback method, please do so and put it in the `FailedHandler` class.

### Coding Style

Try to stick to the overall coding style currently used.
