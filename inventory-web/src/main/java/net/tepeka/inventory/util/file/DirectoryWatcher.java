package net.tepeka.inventory.util.file;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DirectoryWatcher {

  private final List<DirectoryWatcherListener> listener;
  private final WatchService ws;
  private final Path dir;
  private final String contentType;

  public DirectoryWatcher(Path dir, String contentType) throws IOException {
    this.dir = dir;
    this.contentType = contentType;
    listener = new ArrayList<>();
    ws = FileSystems.getDefault().newWatchService();
    dir.register(ws, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
  }

  public void addFileChangeListener(DirectoryWatcherListener listener) {
    this.listener.add(listener);
  }

  public void removeFileChangeListener(DirectoryWatcherListener listener) {
    this.listener.remove(listener);
  }

  public void start() {
    ExecutorService service = Executors.newCachedThreadPool();
    service.submit(new Runnable() {
      @Override
      public void run() {
        while (Thread.interrupted() == false) {
          // wait for key to be signaled
          WatchKey key;
          try {
            key = ws.take();
          } catch (InterruptedException x) {
            return;
          }

          for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == OVERFLOW) {
              continue;
            }
            WatchEvent<Path> ev = cast(event);
            Path filename = ev.context();
            try {
              Path child = dir.resolve(filename);
              if (Files.probeContentType(child).equals(contentType)) {
                if (kind == ENTRY_CREATE) {
                  listener.stream().forEach(l -> l.fileCreated(child));

                } else if (kind == ENTRY_DELETE) {
                  listener.stream().forEach(l -> l.fileDeleted(child));

                } else if (kind == ENTRY_MODIFY) {
                  listener.stream().forEach(l -> l.fileModified(child));
                }
              }
            } catch (IOException x) {
              System.err.println(x);
            }
          }
          boolean valid = key.reset();
          if (!valid) {
            break;
          }
        }
      }
    });

  }

  public void stop() {

  }

  @SuppressWarnings("unchecked")
  private static WatchEvent<Path> cast(WatchEvent<?> event) {
    return (WatchEvent<Path>) event;
  }

}
