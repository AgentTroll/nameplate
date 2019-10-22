# `nameplate`

One of the most common questions I have ever seen on the
Bukkit and Spigot forums is how to change the nameplate
above the player's head. There are various conflicting ways
to achieve this, among them using packets, scoreboards, and
simply using reflection to alter the player's `GameProfile`.

I decided that the only way to end the question once and for
all is to write a single canonical solution that is capable
of handling nameplates longer than 16 characters.

# Implementation

The implementation utilizes ProtocolLib to modify the
player's main nameplate as perceived by other users. The
`GameProfile` modification technique modifies the name
obtained using `#getName()`, which can confuse other plugins
and lead to interesting side effects. The other part of the
implementation involves hooking into the player's existing
scoreboard in order to add a team that would modify the
prefix and suffix for the desired player in the case that
the desired nameplate is longer than 16 characters. This
allows the name to reach 144 characters in length.

# Building

``` shell
git clone https://github.com/AgentTroll/nameplate.git
cd nameplate
mvn clean install
```

The jar output is located in `target/Nameplate.jar`.

# Caveats

- Does not retain skin textures across name changes. If this
is desired, it is possible to alter the `GameProfile` in
ProtocolLib listener to use the same skin. This is left as
an exercise to the reader.
- The implementation requires an iteration over every single
player on the server to refresh the player data. Most people
can get away with only refreshing only players in the same
world or within a certain view distance, but I've left this
up to the reader to decide for themselves.
- Coloring is not consistent for nameplates over 16
characters simply due to the fact that they need to be
broken up across multiple appendages to work. Again, this
is also possible to solve programmatically, and is left as
an exercise to the reader.
- It's not possible to get around having to change the tab
list name in addition to the nameplate; this appears to be
a Vanilla limitation.
- Scoreboard entries must use the "newName" or the nameplate
name minus the scoreboard appendages for the same reason.
The player who changes their nameplate can only be
identified by other clients by that name, so the nameplate
must be used in `#addEntry(...)` calls, not the actual
player name. Of course, this can be changed if you bother to
hack into `GameProfile` and do it yourself.

# Credits

Built with [IntelliJ IDEA](https://jetbrains.com/idea)
