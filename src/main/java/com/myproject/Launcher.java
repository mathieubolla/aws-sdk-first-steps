package com.myproject;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.regions.*;
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
import com.amazonaws.services.s3.model.Region;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.util.Date;

import static java.net.InetAddress.getLocalHost;
import static javax.xml.bind.DatatypeConverter.printBase64Binary;

public class Launcher {

    public static final String CODE_MYPROJECT_COM = "code.myproject.com";
    public static final String JAR_FILE = "aws-sdk-first-steps-1.0-SNAPSHOT-jar-with-dependencies.jar";

    public static void main(String... args) throws IOException {
        AWSCredentialsProvider credentials = getAwsCredentials();

        AmazonS3 storage = new AmazonS3Client(credentials);

        if ("launch".equals(args[0])) {
            checkCreateBucket(storage, CODE_MYPROJECT_COM); // Moved in local machine running code to avoid permission denied on EC2 machines

            upload(storage, new FileInputStream("./target/" + JAR_FILE), CODE_MYPROJECT_COM, JAR_FILE, "application/java-archive", CannedAccessControlList.Private);

            run(credentials, "./shell/startupScript.sh", 1, setupRunnerSecurity(credentials));
        } else if ("run".equals(args[0])) {
            upload(storage, new ByteArrayInputStream("I was there".getBytes()), CODE_MYPROJECT_COM, new Date().toString() + " " + getLocalHost().getHostName(), "text/plain", CannedAccessControlList.Private);
        } else {
            System.out.println("Huh hoh! Don't know what you intended to do...");
        }
    }

    private static void run(AWSCredentialsProvider credentials, String pathToScript, int count, String profileArn) throws IOException {
        AmazonEC2 machines = new AmazonEC2Client(credentials);
        machines.setRegion(com.amazonaws.regions.Region.getRegion(Regions.EU_WEST_1));
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
            storage.createBucket(new CreateBucketRequest(bucket, Region.EU_Ireland));
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

    private static String setupRunnerSecurity(AWSCredentialsProvider credentials) throws IOException {
        AmazonIdentityManagement identityManagement = new AmazonIdentityManagementClient(credentials);

        try {
            identityManagement.createRole(new CreateRoleRequest().withRoleName("runner").withAssumeRolePolicyDocument(FileUtils.readFileToString(new File("./securityPolicies/assumePolicy.txt"))));
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            identityManagement.putRolePolicy(new PutRolePolicyRequest().withRoleName("runner").withPolicyName("codeDownloader").withPolicyDocument(FileUtils.readFileToString(new File("./securityPolicies/codeDownloader.txt"))));
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            identityManagement.putRolePolicy(new PutRolePolicyRequest().withRoleName("runner").withPolicyName("fileUploader").withPolicyDocument(FileUtils.readFileToString(new File("./securityPolicies/fileUploader.txt"))));
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
}
