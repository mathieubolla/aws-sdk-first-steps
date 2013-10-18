package com.myproject;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;

import java.io.FileNotFoundException;
import java.io.InputStream;

import static com.amazonaws.services.s3.model.Region.EU_Ireland;

public class S3Utils {
    public static void upload(AmazonS3 storage, InputStream inputStream, String bucketName, String key, String contentType, CannedAccessControlList acl) throws FileNotFoundException {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(contentType);
        storage.putObject(
                new PutObjectRequest(bucketName, key, inputStream, objectMetadata)
                        .withCannedAcl(acl));
    }

    public static void checkCreateBucket(AmazonS3 storage, String bucket) {
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
}
