package com.myproject;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;

import java.io.File;
import java.io.IOException;

public class CredentialsUtils {
    public static AWSCredentialsProvider getAwsCredentials() {
        try {
            return new StaticCredentialsProvider(new PropertiesCredentials(new File(System.getProperty("user.home"), ".ec2/credentials.properties")));
        } catch (IOException e) {
            return new DefaultAWSCredentialsProviderChain();
        }
    }
}
