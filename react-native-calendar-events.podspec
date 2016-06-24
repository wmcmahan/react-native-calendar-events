Pod::Spec.new do |s|
  s.name         = 'react-native-calendar-events'
  s.version      = '0.1.4'
  s.summary      = 'React Native Module for IOS Calendar Events'
  s.homepage     = 'https://github.com/wmcmahan/react-native-calendar-events'
  s.platform     = :ios, '8.0'
  s.source       = {
    :git => 'https://github.com/wmcmahan/react-native-calendar-events'
  }
  s.source_files  = '*.{h,m}'

  s.dependency 'React'
end
