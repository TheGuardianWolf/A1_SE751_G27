package facegallery;

import apt.annotations.Future;
import apt.annotations.TaskInfoType;
import facegallery.utils.ByteArray;
import pu.loopScheduler.*;

import java.io.File;
import java.nio.file.Files;

public class Experiments {
    private File[] getImageListFromDir(String filePath) {
        return new File(filePath).listFiles((File dir, String name) -> {
            String lowerName = name.toLowerCase();
            return lowerName.endsWith(".jpg") || lowerName.endsWith(".png") || lowerName.endsWith(".gif");
        });
    }

    public ByteArray[] loadImagesAsync(String filePath) {
        File[] fileList = getImageListFromDir(filePath);

        @Future(taskType = TaskInfoType.INTERACTIVE)
        ByteArray[] imageBytes = new ByteArray[fileList.length];
        for (int i = 0; i < imageBytes.length; i++) {
            @Future(taskType = TaskInfoType.INTERACTIVE)
            ByteArray bytes = worker(fileList[i]);
            imageBytes[i] = bytes;
        }
        for (int i = 0; i < imageBytes.length; i++) {
            System.out.println(Integer.toString(i) + ". " + fileList[i].toString() + ": " + Boolean.toString(imageBytes[i] != null));
        }
        return imageBytes;
    }

    public ByteArray[] loadImagesAsyncChunked(String filePath) {
        File[] fileList = getImageListFromDir(filePath);
        ByteArray[] imageBytes = new ByteArray[fileList.length];

        if (fileList.length > 0) {
            LoopScheduler scheduler = LoopSchedulerFactory
                    .createLoopScheduler(
                            0,
                            fileList.length,
                            1,
                            fileList.length,
                            8,
                            AbstractLoopScheduler.LoopCondition.LessThan,
                            LoopSchedulerFactory.LoopSchedulingType.Static
                    );

            @Future(taskType = TaskInfoType.MULTI_IO, taskCount = 8, reduction = "AND")
            Boolean v = loadImagesWorker(fileList, imageBytes, scheduler);
            System.out.println(v);
            for (int i = 0; i < imageBytes.length; i++) {
                System.out.println(Integer.toString(i) + ". " + fileList[i].toString() + ": " + Boolean.toString(imageBytes[i] != null));
            }
        }

        return imageBytes;
    }

    public ByteArray worker(File file) {
        try {
            return new ByteArray(Files.readAllBytes(file.toPath()));
        } catch (Exception e) {
            return new ByteArray(null);
        }
    }

    public Boolean loadImagesWorker(File[] fileList, ByteArray[] imageBytes, LoopScheduler scheduler) {

        LoopRange range = scheduler.getChunk(ThreadID.getStaticID());

        for (int i = range.loopStart; i < range.loopEnd; i += range.localStride) {
            try {
                imageBytes[i] = new ByteArray(Files.readAllBytes(fileList[i].toPath()));
            } catch (Exception e) {
                System.err.println(e.getLocalizedMessage());
            }
        }
        return true;
    }
}
