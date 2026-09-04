package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.TestSupport.MockCommandSender;
import com.cadiducho.cservidoresmc.cmd.CSCommand;
import com.cadiducho.cservidoresmc.cmd.CSCommand.CommandResult;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestCSCommand {

    @Test
    void authorizedWhenSenderHasPermission() {
        CSCommand cmd = new TestCmd("test", "perm.test", Collections.emptyList(), "desc", "help");
        MockCommandSender sender = MockCommandSender.player("player1", "perm.test");
        assertTrue(cmd.isAuthorized(sender));
    }

    @Test
    void notAuthorizedWhenSenderLacksPermission() {
        CSCommand cmd = new TestCmd("test", "perm.test", Collections.emptyList(), "desc", "help");
        MockCommandSender sender = MockCommandSender.player("player1");
        assertFalse(cmd.isAuthorized(sender));
    }

    @Test
    void authorizedWhenCommandHasNoPermission() {
        CSCommand cmd = new TestCmd("test", null, Collections.emptyList(), "desc", "help");
        MockCommandSender sender = MockCommandSender.player("player1");
        assertTrue(cmd.isAuthorized(sender));
    }

    @Test
    void authorizedWithWildcard() {
        CSCommand cmd = new TestCmd("test", "perm.test", Collections.emptyList(), "desc", "help");
        MockCommandSender sender = MockCommandSender.player("player1", "*");
        assertTrue(cmd.isAuthorized(sender));
    }

    @Test
    void metadataFieldsAreAccessible() {
        CSCommand cmd = new TestCmd("mycommand", "my.permission",
                java.util.Arrays.asList("alias1", "alias2"),
                "Short", "Long help");
        assertEquals("mycommand", cmd.getName());
        assertEquals("my.permission", cmd.getPermission());
        assertEquals(2, cmd.getAliases().size());
        assertEquals("alias1", cmd.getAliases().get(0));
        assertEquals("alias2", cmd.getAliases().get(1));
        assertEquals("Short", cmd.getShortDescription());
        assertEquals("Long help", cmd.getHelp());
    }

    @Test
    void tabCompleteReturnsEmptyListByDefault() {
        CSCommand cmd = new TestCmd("test", null, Collections.emptyList(), "d", "h");
        MockCommandSender sender = MockCommandSender.player("p");
        List<String> result = cmd.tabCompleteCommand(sender, Collections.emptyList());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void commandResultEnumHasExpectedValues() {
        CommandResult[] values = CommandResult.values();
        assertEquals(5, values.length);
        assertNotNull(CommandResult.valueOf("SUCCESS"));
        assertNotNull(CommandResult.valueOf("NO_PERMISSION"));
        assertNotNull(CommandResult.valueOf("ONLY_PLAYER"));
        assertNotNull(CommandResult.valueOf("COOLDOWN"));
        assertNotNull(CommandResult.valueOf("ERROR"));
    }

    private static class TestCmd extends CSCommand {
        TestCmd(String name, String perm, List<String> aliases, String shortDesc, String help) {
            super(name, perm, aliases, shortDesc, help);
        }
        @Override
        public CommandResult execute(com.cadiducho.cservidoresmc.api.CSPlugin plugin,
                                     com.cadiducho.cservidoresmc.api.CSCommandSender sender,
                                     String label, List<String> args) {
            return CommandResult.SUCCESS;
        }
    }
}
