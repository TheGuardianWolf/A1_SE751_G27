package facegallery;

import apt.annotations.AsyncCatch;
import apt.annotations.Future;
import apt.annotations.Task;
import apt.annotations.TaskInfoType;
import facegallery.utils.ByteArray;
import pu.loopScheduler.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ImageBytesReader {
    private File[] fileList;
    private ByteArray[] imageBytes;

    public ImageBytesReader(String folderPath) {
        fileList = getImageListFromDir(folderPath);
        imageBytes = new ByteArray[fileList.length];
    }

    public File[] getFileList() {
        return fileList;
    }

    public ByteArray[] getImageBytes() {
        return imageBytes;
    }

    public ByteArray[] run() {
        ByteArray[] imageBytes = new ByteArray[fileList.length];

        for (int i = 0; i < fileList.length; i++) {
            try {
                imageBytes[i] = new ByteArray(Files.readAllBytes(fileList[i].toPath()));
            }
            catch (IOException e) {
                System.err.println(e.getLocalizedMessage());
                imageBytes[i] = null;
            }
        }

        return imageBytes;
    }

    public BlockingQueue<Integer> runAsync() {
        BlockingQueue<Integer> readyQueue = new LinkedBlockingQueue<>();

        LoopScheduler scheduler = LoopSchedulerFactory
                .createLoopScheduler(
                        0,
                        imageBytes.length,
                        1,
                        imageBytes.length,
                        2,
                        AbstractLoopScheduler.LoopCondition.LessThan,
                        LoopSchedulerFactory.LoopSchedulingType.Static
                );

        @Future(taskType = TaskInfoType.MULTI_IO, taskCount = 2, reduction = "AND")
        @AsyncCatch(throwables = {IOException.class}, handlers = {"asyncExceptionHandler()"})
        Boolean sync = asyncWorker(scheduler, readyQueue);

        return readyQueue;
    }

    public Boolean runAsync(Void wait) {
        BlockingQueue<Integer> readyQueue = new LinkedBlockingQueue<>();

        LoopScheduler scheduler = LoopSchedulerFactory
                .createLoopScheduler(
                        0,
                        imageBytes.length,
                        1,
                        2,
                        AbstractLoopScheduler.LoopCondition.LessThan,
                        LoopSchedulerFactory.LoopSchedulingType.Static
                );

        @Future(taskType = TaskInfoType.MULTI_IO, taskCount = 2, reduction = "AND")
        @AsyncCatch(throwables = {IOException.class}, handlers = {"asyncExceptionHandler()"})
        Boolean sync = asyncWorker(scheduler);

        return sync;
    }

    private File[] getImageListFromDir(String filePath) {
        return new File(filePath).listFiles((File dir, String name) -> {
            String lowerName = name.toLowerCase();
            return lowerName.endsWith(".jpg") || lowerName.endsWith(".png") || lowerName.endsWith(".gif");
        });
    }

    @Task
    private Boolean asyncWorker(LoopScheduler scheduler) {
        LoopRange range = scheduler.getChunk(ThreadID.getStaticID());

        for (int i = range.loopStart; i < range.loopEnd; i += range.localStride) {
            try {
                imageBytes[i] = new ByteArray(Files.readAllBytes(fileList[i].toPath()));
            } catch (IOException e) {
                System.err.println(e.getLocalizedMessage());
                imageBytes[i] = null;
            }
        }

        return true;
    }

    @Task
    private Boolean asyncWorker(LoopScheduler scheduler, BlockingQueue<Integer> readyQueue) {
        LoopRange range = scheduler.getChunk(ThreadID.getStaticID());

        for (int i = range.loopStart; i < range.loopEnd; i += range.localStride) {
            try {
                imageBytes[i] = new ByteArray(Files.readAllBytes(fileList[i].toPath()));
            } catch (IOException e) {
                System.err.println(e.getLocalizedMessage());
                imageBytes[i] = null;
            } finally {
                readyQueue.offer(i);
            }
        }

        return true;
    }

    private static void asyncExceptionHandler() {
        System.out.println("Exception");
    }
}
