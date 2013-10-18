package com.myproject;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;

import java.io.*;

public class Launcher {

    public static final String CODE_MYPROJECT_COM = "code.myproject.com";

    public static void main(String... args) throws IOException {
        AWSCredentials credentials = new PropertiesCredentials(new File(new File(System.getProperty("user.home"), ".ec2"), "credentials.properties"));

        AmazonS3 storage = new AmazonS3Client(credentials);
        checkCreateBucket(storage, CODE_MYPROJECT_COM);

        upload(storage, new FileInputStream("./target/aws-sdk-first-steps-1.0-SNAPSHOT-jar-with-dependencies.jar"), CODE_MYPROJECT_COM, "myproject-com.jar", "application/java-archive", CannedAccessControlList.Private);
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
}
