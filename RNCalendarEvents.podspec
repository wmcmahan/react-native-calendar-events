require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "RNCalendarEvents"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/wmcmahan/react-native-calendar-events.git", :tag => "#{s.version}" }

  s.source_files = "ios/*.{h,m,mm,swift}"
  s.private_header_files = "ios/**/*.h"
  s.resource_bundles = { "RNCalendarEventsPrivacy" => "ios/PrivacyInfo.xcprivacy" }

  s.frameworks = "EventKit"
  s.swift_version = "5.9"
  s.pod_target_xcconfig = {
    "DEFINES_MODULE" => "YES",
  }

  install_modules_dependencies(s)

  s.test_spec "Tests" do |test_spec|
    test_spec.requires_app_host = true
    test_spec.source_files = "ios/Tests/**/*.swift"
    test_spec.frameworks = "XCTest", "EventKit"
  end
end
