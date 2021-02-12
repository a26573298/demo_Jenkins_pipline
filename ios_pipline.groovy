void unlock_keychain(){
    sh"""
        security unlock-keychain -p "utapass" "/Users/utapass-sqa/Library/Keychains/login.keychain-db"
    """
}


void clone_ui_test_source_code(){

    dir( "src/utapass_ios" ) {
        git url: 'git@github.com:KKBOX/utapass_ios.git',
            branch: 'dev-UITesting-SQA',
            credentialsId: '479f3fbf-53d8-4285-be72-6c4e7566313b'
        
        sh"""
            git submodule update --init
        """
    }

}

void run_xcui_test(){
    sh'''
        PROJECT="src/utapass_ios/UtaPass/UtaPass.xcodeproj"
        SCHEME="UtaPassUITests"
        CONFIGURATION="Debug"
        iPhoneXSMax_DEVICE_ID="5ee54c29c340eeb8ecddaa74230e002b67edf8dc"
        iPad_02_DEVICE_NAME="Uta 的 iPhone (2)"
    

        xcodebuild test -project "${PROJECT}" \
            -scheme "${SCHEME}" \
            -configuration "${CONFIGURATION}" \
            -destination platform="iOS",id="${iPhoneXSMax_DEVICE_ID}" \
            -enableCodeCoverage "YES" \
            -resultBundlePath src/utapass_ios/UtaPass/TestResults/report_"${BUILD_NUMBER}"/report_"${BUILD_NUMBER}" | xcpretty -s
    '''
}

void publish_html_report(){
    dir( "src/utapass_ios/UtaPass" ) {
            sh'''
                xchtmlreport -r TestResults/report_"${BUILD_NUMBER}"/report_"${BUILD_NUMBER}"
            '''
            
            publishHTML target: [
                allowMissing: false,
                alwaysLinkToLastBuild: false,
                keepAll: true,
                reportDir: "",
                reportFiles: "TestResults/report_${BUILD_NUMBER}/index.html",
                reportName: "SmokeReport"
            ]   
        } 
}

void send_message( String report_name, String fail_count ) {
    url    = " <https://utapass-jenkins.kkinternal.com/view/SQA/job/UtaPassAndroidDevEspresso/${ESPRESSO_BUILD_NUMBER}/${report_name}_20Report/|${report_name} Report Click Me>"
    title  = "【 UtaPass Automation - ( ${report_name} ) 】\n\n"
    result = " Test Result                                 TestBuild \n Failed: ${failcount} 條                               UtaPassAndroid Dev #*${DEV_BUILD_NUMBER}* \n\n\n"
    report = " Test Report \n ${url}"
    
    slackSend channel: '#up_sqa_auto',
              tokenCredentialId: '7da62e8e-efca-48eb-b162-eb685da6559d',
              color: 'danger',
              message: "${title}${result}${report}"
}

void send_message_when_job_fail() {
    title  = "【 UtaPass_ios Automation 】\n\n"
    result = " * JOB Fail 趕快來看！！ *\n\n"
    report = " https://utapass-jenkins.kkinternal.com/view/SQA/job/UtaPassAndroidDevEspresso/${ESPRESSO_BUILD_NUMBER}/"

    slackSend channel: '#up_sqa_auto',
              tokenCredentialId: '7da62e8e-efca-48eb-b162-eb685da6559d',
              color: 'danger',
              message: "${title}${result}${report}"
}


try {
    node( 'SQA-android3' ) {

        stage( 'Deploy' ) {
            unlock_keychain()
            clone_ui_test_source_code()
        }

        stage( 'Build & Test' ) {
            try {
                run_xcui_test()
            }
            catch( Exception err ) {
                publish_html_report()
            }
        }

        stage( 'Report' ) {
            publish_html_report()
        }

    }
}

catch( Exception err ) {
    currentBuild.result = 'FAILURE'
}