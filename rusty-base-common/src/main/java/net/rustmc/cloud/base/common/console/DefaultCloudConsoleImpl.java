package net.rustmc.cloud.base.common.console;

import net.rustmc.cloud.base.common.Rust;
import net.rustmc.cloud.base.common.events.natives.CloudNativeConsoleInputEvent;
import net.rustmc.cloud.base.console.CloudConsoleColor;
import net.rustmc.cloud.base.console.ICloudConsole;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.function.Consumer;

/**
 * This class belongs to the rusty-cloud project
 *
 * @author Alexander Jilge
 * @since 23.10.2022
 */
@SuppressWarnings("FieldCanBeLocal")
public class DefaultCloudConsoleImpl implements ICloudConsole {

    private final LineReader lineReader = LineReaderBuilder.builder().completer(new DefaultConsoleCompleter()).build();
    private final String prompt = "» ";
    private final LinkedList<Consumer<String>> handlers = new LinkedList<>();
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final Thread thread = new Thread(() -> {
        String line;
        while ((line = lineReader.readLine(this.prompt)) != null) {
            for (Consumer<String> handler : handlers) handler.accept(line);
            Rust.getInstance().getEventPerformer().perform(new CloudNativeConsoleInputEvent(line));
        }
    });

    public DefaultCloudConsoleImpl() {
        this.clear();
        lineReader.setAutosuggestion(LineReader.SuggestionType.COMPLETER);
    }

    @Override
    public ICloudConsole send(String output, Output level) {
        output = this._color(output);
        switch (level) {
            case INFO -> output = " | " +  dateTimeFormatter.format(LocalDateTime.now()) + " | " + CloudConsoleColor.GREEN.getAnsiCode() + "INFO" + CloudConsoleColor.RESET + " » " + output + CloudConsoleColor.RESET;
            case ERROR -> output = " | " +  dateTimeFormatter.format(LocalDateTime.now()) + " | " + CloudConsoleColor.RED.getAnsiCode() + "ERRO" + CloudConsoleColor.RESET + " » " + CloudConsoleColor.RESET + output + CloudConsoleColor.RESET;
            case WARN -> output = " | " +  dateTimeFormatter.format(LocalDateTime.now()) + " | " + CloudConsoleColor.YELLOW.getAnsiCode() + "WARN" + CloudConsoleColor.RESET + " » " + CloudConsoleColor.RESET + output + CloudConsoleColor.RESET;
        }
        lineReader.getTerminal().puts(InfoCmp.Capability.carriage_return);
        lineReader.getTerminal().writer().println(output);
        lineReader.getTerminal().flush();
        if (lineReader.isReading()) {
            lineReader.callWidget(org.jline.reader.LineReader.REDRAW_LINE);
            lineReader.callWidget(org.jline.reader.LineReader.REDISPLAY);
        }
        return this;
    }

    @Override
    public ICloudConsole send(String output) {
        return send(output, Output.INFO);
    }

    @Override
    public ICloudConsole send(String service, String output) {
        return this;
    }

    @Override
    public void clear() {
        if (Rust.getInstance().getOperatingSystem().contains("Windows")) {
            try {
                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "cls");
                Process startProcess = pb.inheritIO().start();
                startProcess.waitFor();
            } catch (RuntimeException exception) {
                exception.printStackTrace();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                ProcessBuilder pb = new ProcessBuilder("clear");
                Process startProcess = pb.inheritIO().start();
                startProcess.waitFor();
            } catch (RuntimeException exception) {
                exception.printStackTrace();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String _color(String code) {
        return code
                .replace("§c", CloudConsoleColor.RED.getAnsiCode())
                .replace("§a", CloudConsoleColor.GREEN.getAnsiCode())
                .replace("§e", CloudConsoleColor.YELLOW.getAnsiCode())
                .replace("§6", CloudConsoleColor.ORANGE.getAnsiCode())
                .replace("§r", CloudConsoleColor.RESET.getAnsiCode())
                .replace("§7", CloudConsoleColor.RESET.getAnsiCode())
                .replace("§b", CloudConsoleColor.CYAN.getAnsiCode()
                );
    }

    @Override
    public void run() {
        this.thread.start();
    }

    @Override
    public void close() {
        lineReader.getTerminal().reader().shutdown();
        lineReader.getTerminal().pause();
        this.thread.interrupt();
    }

    @Override
    public ICloudConsole push(Consumer<String> handler) {
        this.handlers.push(handler);
        return this;
    }
}
