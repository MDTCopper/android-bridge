package copper.bridge.util;

import copper.bridge.func.*;
import java.util.*;

/**
 * Command-line argument parser: short/long options, flags, bundled flags ({@code -abc}) and the bare words
 * that are none of those, {@code --} included, which collects them. The constructor registers
 * {@code -h/--help}.
 */
public class ArgParser {
    private final String programName;
    private final String description;
    private final List<Option> options = new ArrayList<>();
    private final List<String> positionalArgs = new ArrayList<>();
    private boolean helpRequested = false;
    private String positionalDescription = null;

    public ArgParser(String programName, String description) {
        this.programName = programName;
        this.description = description;
        addFlag("h", "help", "Show this help message", () -> helpRequested = true);
    }

    /**
     * Sets what the bare words are for, as the help should word it. The parser collects them and knows
     * nothing more, so only the caller can say whose they are.
     */
    public void setPositionalDescription(String positionalDescription) {
        this.positionalDescription = positionalDescription;
    }

    /** Registers a flag (an option without an argument) and the action it runs. */
    public void addFlag(String shortOpt, String longOpt, String desc, Runnable action) {
        options.add(new Option(shortOpt, longOpt, desc, false, null, action, null));
    }

    /** Registers an option with an argument and the handler it calls. */
    public void addOption(String shortOpt, String longOpt, String desc, String argName, Cons<String> action) {
        options.add(new Option(shortOpt, longOpt, desc, true, argName, null, action));
    }

    /**
     * Registers an option whose value may itself start with a dash: {@code -J -Xmx2g} has to hand
     * {@code -Xmx2g} over untouched, so only options carrying foreign arguments use this overload.
     */
    public void addOption(String shortOpt, String longOpt, String desc, String argName, Cons<String> action, boolean allowDashValue) {
        options.add(new Option(shortOpt, longOpt, desc, true, argName, null, action, allowDashValue));
    }

    /** Parses command-line arguments.
     *
     * @throws IllegalArgumentException if an unknown option or missing argument is encountered. */
    public void parse(String[] args) {
        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            if (arg.equals("--")) {
                for (int j = i + 1; j < args.length; j++) {
                    positionalArgs.add(args[j]);
                }
                break;
            } else if (arg.startsWith("--")) {
                String longOpt = arg.substring(2);
                int eqIdx = longOpt.indexOf('=');
                String optName;
                String optValue = null;
                if (eqIdx != -1) {
                    optName = longOpt.substring(0, eqIdx);
                    optValue = longOpt.substring(eqIdx + 1);
                } else {
                    optName = longOpt;
                }
                Option opt = findOptionByLong(optName);
                if (opt == null) {
                    throw new IllegalArgumentException("Unknown option: " + arg);
                }
                if (opt.hasArg && optValue == null) {
                    if (i + 1 < args.length && (opt.allowDashValue || !args[i + 1].startsWith("-"))) {
                        optValue = args[i + 1];
                        i++;
                    } else {
                        throw new IllegalArgumentException("Option " + opt.toDisplayString() + " requires an argument");
                    }
                }
                processOption(opt, optValue);
                i++;
            } else if (arg.startsWith("-") && arg.length() > 1) {
                String optString = arg.substring(1);
                if (optString.length() == 1) {
                    String shortOpt = optString;
                    Option opt = findOptionByShort(shortOpt);
                    if (opt == null) {
                        throw new IllegalArgumentException("Unknown option: " + arg);
                    }
                    String optValue = null;
                    if (opt.hasArg) {
                        if (i + 1 < args.length && (opt.allowDashValue || !args[i + 1].startsWith("-"))) {
                            optValue = args[i + 1];
                            i++;
                        } else {
                            throw new IllegalArgumentException("Option " + arg + " requires an argument");
                        }
                    }
                    processOption(opt, optValue);
                    i++;
                } else {
                    for (int j = 0; j < optString.length(); j++) {
                        char c = optString.charAt(j);
                        String shortOpt = String.valueOf(c);
                        Option opt = findOptionByShort(shortOpt);
                        if (opt == null) {
                            throw new IllegalArgumentException("Unknown option: -" + c);
                        }
                        if (opt.hasArg) {
                            throw new IllegalArgumentException("Bundled option cannot contain an option that requires an argument: -" + c);
                        }
                        processOption(opt, null);
                    }
                    i++;
                }
            } else {
                positionalArgs.add(arg);
                i++;
            }
        }

        if (helpRequested) {
            printHelp();
            System.exit(0);
        }
    }

    private void processOption(Option opt, String value) {
        if (opt.hasArg && value == null) {
            throw new IllegalArgumentException("Option " + opt.toDisplayString() + " requires an argument");
        }
        if (!opt.hasArg && value != null) {
            throw new IllegalArgumentException("Option " + opt.toDisplayString() + " does not accept an argument");
        }

        if (opt.hasArg) {
            if (opt.optionAction != null) {
                opt.optionAction.get(value);
            }
        } else {
            if (opt.flagAction != null) {
                opt.flagAction.run();
            }
        }
    }

    private Option findOptionByShort(String shortOpt) {
        for (Option opt : options) {
            if (shortOpt.equals(opt.shortOpt)) {
                return opt;
            }
        }
        return null;
    }

    private Option findOptionByLong(String longOpt) {
        for (Option opt : options) {
            if (longOpt.equals(opt.longOpt)) {
                return opt;
            }
        }
        return null;
    }

    /** Returns the positional arguments. */
    public List<String> getPositionalArgs() {
        return positionalArgs;
    }

    /** Prints the help message to stdout. */
    public void printHelp() {
        System.out.println("Usage: " + programName + " [options] [arguments...]");
        if (description != null && !description.isEmpty()) {
            System.out.println(description);
        }
        System.out.println("\nOptions:");
        for (Option opt : options) {
            if (opt.shortOpt == null && opt.longOpt == null) continue;
            StringBuilder sb = new StringBuilder("  ");
            if (opt.shortOpt != null) {
                sb.append("-").append(opt.shortOpt);
                if (opt.longOpt != null) sb.append(", ");
            }
            if (opt.longOpt != null) {
                sb.append("--").append(opt.longOpt);
            }
            if (opt.hasArg) {
                sb.append(" <").append(opt.argName != null ? opt.argName : "arg").append(">");
            }
            while (sb.length() < 30) sb.append(' ');
            sb.append(opt.description != null ? opt.description : "");
            System.out.println(sb.toString());
        }
        if (positionalDescription != null) {
            System.out.println("\nBare words are " + positionalDescription + ".");
            System.out.println("A word starting with '-' is read as an option unless '--' comes before it.");
        }
    }

    /** Internal option representation. */
    private static class Option {
        final String shortOpt;
        final String longOpt;
        final String description;
        final boolean hasArg;
        final String argName;
        final Runnable flagAction;
        final Cons<String> optionAction;
        /** When {@code true}, a value starting with {@code -} is accepted as this option's argument. */
        final boolean allowDashValue;

        Option(String shortOpt, String longOpt, String description, boolean hasArg,
               String argName, Runnable flagAction, Cons<String> optionAction) {
            this(shortOpt, longOpt, description, hasArg, argName, flagAction, optionAction, false);
        }

        Option(String shortOpt, String longOpt, String description, boolean hasArg,
               String argName, Runnable flagAction, Cons<String> optionAction, boolean allowDashValue) {
            this.shortOpt = shortOpt;
            this.longOpt = longOpt;
            this.description = description;
            this.hasArg = hasArg;
            this.argName = argName;
            this.flagAction = flagAction;
            this.optionAction = optionAction;
            this.allowDashValue = allowDashValue;
        }

        String toDisplayString() {
            return (shortOpt != null) ? "-" + shortOpt : "--" + longOpt;
        }
    }
}
