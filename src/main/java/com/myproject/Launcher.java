package com.myproject;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

import static com.myproject.UrlUtils.download;

public class Launcher {
    public static final String CODE_MYPROJECT_COM = "code.myproject.com";
    public static final String JAR_FILE = "aws-sdk-first-steps-1.0-SNAPSHOT-jar-with-dependencies.jar";
    public static final String INPUT_QUEUE = CODE_MYPROJECT_COM.replace('.', '-') + "-input";
    public static final String REPORT_QUEUE = CODE_MYPROJECT_COM.replace('.', '-')+ "-report";

    public static void main(String... args) throws IOException {
        AWSCredentialsProvider credentials = CredentialsUtils.getAwsCredentials();

        AmazonS3 storage = new AmazonS3Client(credentials);
        storage.setRegion(Region.getRegion(Regions.EU_WEST_1));

        AmazonSQS queue = new AmazonSQSClient(credentials);
        queue.setRegion(Region.getRegion(Regions.EU_WEST_1));

        AmazonEC2 machines = new AmazonEC2Client(credentials);
        machines.setRegion(Region.getRegion(Regions.EU_WEST_1));

        AmazonIdentityManagement identityManagement = new AmazonIdentityManagementClient(credentials);
        identityManagement.setRegion(Region.getRegion(Regions.EU_WEST_1));

        if ("launch".equals(args[0])) {
            setup(storage, queue, machines, identityManagement);

            manageAnswers(queue);
        } else if ("run".equals(args[0])) {
            run(storage, queue);
        } else {
            System.out.println("Huh hoh! Don't know what you intended to do...");
        }
    }

    private static void run(AmazonS3 storage, AmazonSQS queue) throws FileNotFoundException, UnknownHostException {
        while (true) {
            String[] message = SQSUtils.readMessageFrom(queue, INPUT_QUEUE);

            String url = message[0];
            String receipt = message[1];

            if ("SUICIDE".equals(url)) {
                SQSUtils.acknowledge(queue, INPUT_QUEUE, receipt);
                break;
            }

            try {
                processQueries(storage, queue, url, receipt);

                SQSUtils.acknowledge(queue, INPUT_QUEUE, receipt);
            } catch (Exception e) {
                // Because message is not acknowledged, it will be reprocessed by someone else soon
                e.printStackTrace();
            }
        }
    }

    private static void setup(AmazonS3 storage, AmazonSQS queue, AmazonEC2 machines, AmazonIdentityManagement identityManagement) throws IOException {
        S3Utils.checkCreateBucket(storage, CODE_MYPROJECT_COM); // Moved in local machine running code to avoid permission denied on EC2 machines
        SQSUtils.checkCreateQueues(queue, INPUT_QUEUE, REPORT_QUEUE);

        S3Utils.upload(storage, new FileInputStream("./target/" + JAR_FILE), CODE_MYPROJECT_COM, JAR_FILE, "application/java-archive", CannedAccessControlList.Private);

        String profileArn = IamUtils.setupRunnerSecurity(identityManagement, CODE_MYPROJECT_COM, SQSUtils.getQueueArn(queue, INPUT_QUEUE), SQSUtils.getQueueArn(queue, REPORT_QUEUE));
        Ec2Utils.run(machines, "./shell/startupScript.sh", 1, profileArn);
    }

    private static void manageAnswers(AmazonSQS queue) {
        String startWith = "https://www.google.fr/?q=maven#q=maven";
        int stopAt = 500;

        SQSUtils.sendMessageTo(queue, INPUT_QUEUE, startWith);

        Set<String> processedUrls = new HashSet<String>(stopAt);
        processedUrls.add(startWith);

        while (true) {
            String[] message = SQSUtils.readMessageFrom(queue, REPORT_QUEUE);
            String url = message[0];
            String receipt = message[1];

            if (processedUrls.contains(url)) {
                SQSUtils.acknowledge(queue, REPORT_QUEUE, receipt);
                continue;
            }

            processedUrls.add(url);
            SQSUtils.sendMessageTo(queue, INPUT_QUEUE, url);
            SQSUtils.acknowledge(queue, REPORT_QUEUE, receipt);

            System.out.println("Asking analysis of "+url);

            if(processedUrls.size() >= stopAt) {
                break;
            }
        }
    }

    private static void processQueries(AmazonS3 storage, final AmazonSQS queue, final String inputUrl, final String inputReceipt) throws FileNotFoundException {
        InputStream data = download(inputUrl, new UrlUtils.FoundCallback<String>() {
            @Override
            public void found(String element) {
                System.err.println("Found " + element);
                SQSUtils.sendMessageTo(queue, REPORT_QUEUE, element);
                SQSUtils.renewTimeout(queue, INPUT_QUEUE, inputReceipt, 20);
            }
        });

        S3Utils.upload(storage, data, CODE_MYPROJECT_COM, "results/" + inputUrl.replace("http://", "").replace("https://", ""), "application/octet-stream", CannedAccessControlList.Private);

        IOUtils.closeQuietly(data);
    }
}
