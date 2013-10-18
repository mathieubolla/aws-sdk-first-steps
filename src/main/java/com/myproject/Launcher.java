package com.myproject;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.ShutdownBehavior;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

import static com.amazonaws.services.s3.model.Region.EU_Ireland;
import static com.myproject.UrlUtils.download;
import static javax.xml.bind.DatatypeConverter.printBase64Binary;

public class Launcher {

    public static final String CODE_MYPROJECT_COM = "code.myproject.com";
    public static final String JAR_FILE = "aws-sdk-first-steps-1.0-SNAPSHOT-jar-with-dependencies.jar";
    public static final String INPUT_QUEUE = CODE_MYPROJECT_COM.replace('.', '-') + "-input";
    public static final String REPORT_QUEUE = CODE_MYPROJECT_COM.replace('.', '-')+ "-report";

    public static void main(String... args) throws IOException {
        AWSCredentialsProvider credentials = getAwsCredentials();

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
        } else if ("run".equals(args[0])) {
            run(storage, queue);
        } else {
            System.out.println("Huh hoh! Don't know what you intended to do...");
        }
    }

    private static void run(AmazonEC2 machines, String pathToScript, int count, String profileArn) throws IOException {
        machines.runInstances(
            new RunInstancesRequest()
                .withImageId("ami-c7c0d6b3") // This used to be the official, Ireland running, 32 bit Amazon Machine Image. Or pick, for instance, [Ubuntu](http://cloud-images.ubuntu.com/locator/ec2/)
                .withInstanceType(InstanceType.T1Micro) // Smallest possible, cheapest. Be warned: Cc28xlarge can set you back 3.75$ per call per machine per hour... [Pricing](http://aws.amazon.com/fr/ec2/#pricing)
                .withMaxCount(count)
                .withMinCount(count)
                .withInstanceInitiatedShutdownBehavior(ShutdownBehavior.Terminate)
                .withIamInstanceProfile(new IamInstanceProfileSpecification().withArn(profileArn))
                .withUserData(printBase64Binary(FileUtils.readFileToString(new File(pathToScript), "UTF-8").getBytes("UTF-8")))
        );
    }

    private static void upload(AmazonS3 storage, InputStream inputStream, String bucketName, String key, String contentType, CannedAccessControlList acl) throws FileNotFoundException {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(contentType);
        storage.putObject(
                new PutObjectRequest(bucketName, key, inputStream, objectMetadata)
                        .withCannedAcl(acl));
    }

    private static void checkCreateBucket(AmazonS3 storage, String bucket) {
        if (!exists(storage, bucket)) {
            storage.createBucket(new CreateBucketRequest(bucket, EU_Ireland));
        }
    }

    private static boolean exists(AmazonS3 storage, String bucket) {
        for (Bucket existingBucket : storage.listBuckets()) {
            if (existingBucket.getName().equals(bucket)) {
                return true;
            }
        }
        return false;
    }

    private static AWSCredentialsProvider getAwsCredentials() {
        try {
            return new StaticCredentialsProvider(new PropertiesCredentials(new File(System.getProperty("user.home"), ".ec2/credentials.properties")));
        } catch (IOException e) {
            return new DefaultAWSCredentialsProviderChain();
        }
    }

    private static String setupRunnerSecurity(AmazonIdentityManagement identityManagement, String bucketName, String inputArn, String reportArn) throws IOException {
        try {
            identityManagement.createRole(new CreateRoleRequest().withRoleName("runner").withAssumeRolePolicyDocument(FileUtils.readFileToString(new File("./securityPolicies/assumePolicy.txt"))));
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            identityManagement.putRolePolicy(new PutRolePolicyRequest().withRoleName("runner").withPolicyName("codeDownloader").withPolicyDocument(templateALaMustache(FileUtils.readFileToString(new File("./securityPolicies/codeDownloader.txt")), "bucketName", bucketName)));
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            identityManagement.putRolePolicy(new PutRolePolicyRequest().withRoleName("runner").withPolicyName("fileUploader").withPolicyDocument(templateALaMustache(FileUtils.readFileToString(new File("./securityPolicies/fileUploader.txt")), "bucketName", bucketName)));
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            String policy = FileUtils.readFileToString(new File("./securityPolicies/queueRunner.txt"));

            policy = templateALaMustache(policy, "inputArn", inputArn);
            policy = templateALaMustache(policy, "reportArn", reportArn);

            System.err.println(policy);

            identityManagement.putRolePolicy(new PutRolePolicyRequest().withRoleName("runner").withPolicyName("fileUploader").withPolicyDocument(policy));
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            identityManagement.createInstanceProfile(new CreateInstanceProfileRequest().withInstanceProfileName("runnerProfile"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            identityManagement.addRoleToInstanceProfile(new AddRoleToInstanceProfileRequest().withInstanceProfileName("runnerProfile").withRoleName("runner"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            GetInstanceProfileResult runnerProfile = identityManagement.getInstanceProfile(new GetInstanceProfileRequest().withInstanceProfileName("runnerProfile"));
            return runnerProfile.getInstanceProfile().getArn();
        } catch (Exception e) {
            e.printStackTrace();
        }

        throw new RuntimeException("Could not create instance profile");
    }

    private static String templateALaMustache(String template, String variable, String value) {
        return StringUtils.replace(template, "{{" + variable + "}}", value);
    }

    private static void run(AmazonS3 storage, AmazonSQS queue) throws FileNotFoundException, UnknownHostException {
        while (true) {
            String[] message = readMessageFrom(queue, INPUT_QUEUE);

            String url = message[0];
            String receipt = message[1];

            if ("SUICIDE".equals(url)) {
                acknowledge(queue, INPUT_QUEUE, receipt);
                break;
            }

            try {
                process(storage, queue, url, receipt);

                acknowledge(queue, INPUT_QUEUE, receipt);
            } catch (Exception e) {
                // Because message is not acknowledged, it will be reprocessed by someone else soon
                e.printStackTrace();
            }
        }
    }

    private static void setup(AmazonS3 storage, AmazonSQS queue, AmazonEC2 machines, AmazonIdentityManagement identityManagement) throws IOException {
        checkCreateBucket(storage, CODE_MYPROJECT_COM); // Moved in local machine running code to avoid permission denied on EC2 machines
        checkCreateQueues(queue, INPUT_QUEUE, REPORT_QUEUE);

        upload(storage, new FileInputStream("./target/" + JAR_FILE), CODE_MYPROJECT_COM, JAR_FILE, "application/java-archive", CannedAccessControlList.Private);

        run(machines, "./shell/startupScript.sh", 1, setupRunnerSecurity(identityManagement, CODE_MYPROJECT_COM, getQueueArn(queue, INPUT_QUEUE), getQueueArn(queue, REPORT_QUEUE)));

        String startWith = "https://www.google.fr/?q=maven#q=maven";
        int stopAt = 500;

        sendMessageTo(queue, INPUT_QUEUE, startWith);

        Set<String> processedUrls = new HashSet<String>(stopAt);
        processedUrls.add(startWith);

        while (true) {
            String[] message = readMessageFrom(queue, REPORT_QUEUE);
            String url = message[0];
            String receipt = message[1];

            if (processedUrls.contains(url)) {
                acknowledge(queue, REPORT_QUEUE, receipt);
                continue;
            }

            processedUrls.add(url);
            sendMessageTo(queue, INPUT_QUEUE, url);
            acknowledge(queue, REPORT_QUEUE, receipt);

            System.out.println("Asking analysis of "+url);

            if(processedUrls.size() >= stopAt) {
                break;
            }
        }
    }

    private static void checkCreateQueues(AmazonSQS queue, String... queueNames) {
        for (String queueName : queueNames) {
            queue.createQueue(new CreateQueueRequest().withQueueName(queueName));
        }
    }

    private static String getQueueUrl(AmazonSQS queue, String queueName) {
        return queue.getQueueUrl(new GetQueueUrlRequest().withQueueName(queueName)).getQueueUrl();
    }

    private static String getQueueArn(AmazonSQS queue, String queueName) {
        return queue.getQueueAttributes(new GetQueueAttributesRequest().withAttributeNames("QueueArn").withQueueUrl(getQueueUrl(queue, queueName))).getAttributes().get("QueueArn");
    }

    private static void sendMessageTo(AmazonSQS queue, String queueName, String message) {
        queue.sendMessage(new SendMessageRequest().withQueueUrl(getQueueUrl(queue, queueName)).withMessageBody(message));
    }

    private static String[] readMessageFrom(AmazonSQS queue, String queueName) {
        ReceiveMessageResult receiveMessageResult = queue.receiveMessage(
                new ReceiveMessageRequest()
                        .withQueueUrl(getQueueUrl(queue, queueName))
                        .withMaxNumberOfMessages(1) // Process one at a time
                        .withWaitTimeSeconds(20) // Use long polling client
        );
        if (receiveMessageResult.getMessages().isEmpty()) {
            return readMessageFrom(queue, queueName);
        }

        Message message = receiveMessageResult.getMessages().get(0);
        return new String[]{message.getBody(), message.getReceiptHandle()};
    }

    private static void acknowledge(AmazonSQS queue, String queueName, String receipt) {
        queue.deleteMessage(new DeleteMessageRequest().withQueueUrl(getQueueUrl(queue, queueName)).withReceiptHandle(receipt));
    }

    private static void renewTimeout(AmazonSQS queue, String queueName, String receipt) {
        queue.changeMessageVisibility(new ChangeMessageVisibilityRequest().withQueueUrl(getQueueUrl(queue, queueName)).withReceiptHandle(receipt).withVisibilityTimeout(20)); // Say I need 20 more seconds to process
    }

    private static void process(AmazonS3 storage, final AmazonSQS queue, final String inputUrl, final String inputReceipt) throws FileNotFoundException {
        InputStream data = download(inputUrl, new UrlUtils.FoundCallback<String>() {
            @Override
            public void found(String element) {
                sendMessageTo(queue, REPORT_QUEUE, element);
                renewTimeout(queue, INPUT_QUEUE, inputReceipt);
            }
        });

        upload(storage, data, CODE_MYPROJECT_COM, "results/" + inputUrl.replace("http://", "").replace("https://", ""), "application/octet-stream", CannedAccessControlList.Private);

        IOUtils.closeQuietly(data);
    }
}
