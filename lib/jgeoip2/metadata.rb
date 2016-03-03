module JGeoIP2
  class Metadata
    attr_reader :format_version,
                :build_time,
                :database_type,
                :languages,
                :ip_version

    def initialize(metadata)
      major = metadata['binary_format_major_version']
      minor = metadata['binary_format_minor_version']
      @format_version = "#{major}.#{minor}"
      @build_time = (t = metadata['build_epoch']) && Time.at(t)
      @database_type = metadata['database_type']
      @languages = metadata['languages']
      @ip_version = metadata['ip_version']
      @descriptions = metadata['description']
    end

    def description(language='en')
      @descriptions[language]
    end
  end
end
