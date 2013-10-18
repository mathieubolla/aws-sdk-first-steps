package com.myproject;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;

public class Launcher {
    public static void main(String... args) {
        AWSCredentials credentials = new BasicAWSCredentials("my-access-key-id", "my-secret-key-VERY-PRIVATE-ISNT-IT");

        AmazonS3 storage = new AmazonS3Client(credentials);
        for (Bucket bucket : storage.listBuckets()) {
            System.out.println(bucket);
        }
    }
}
