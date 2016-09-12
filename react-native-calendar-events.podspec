require 'json'
package_json = JSON.parse(File.read('package.json'))

Pod::Spec.new do |s|
  s.name         = 'react-native-calendar-events'
  s.version      = package_json['version']
  s.summary      = package_json['description']
  s.homepage     = 'https://github.com/wmcmahan/react-native-calendar-events'
  s.author       = package_json['author']
  s.license      = package_json['license']
  s.platform     = :ios, '8.0'
  s.source       = {
    :git => 'https://github.com/wmcmahan/react-native-calendar-events.git'
  }
  s.source_files  = '*.{h,m}'

  s.dependency 'React'
end
