package com.myproject;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;

public class IamUtils {
    public static String setupRunnerSecurity(AmazonIdentityManagement identityManagement, String bucketName, String inputArn, String reportArn) throws IOException {
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

            identityManagement.putRolePolicy(new PutRolePolicyRequest().withRoleName("runner").withPolicyName("queueRunner").withPolicyDocument(policy));
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
}
