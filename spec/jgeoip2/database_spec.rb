# encoding: utf-8

require 'json'

module JGeoIP2
  describe Database do
    let :database do
      described_class.open(db_path)
    end

    let :db_path do
      File.expand_path('../../resources/maxmind-db/test-data/GeoIP2-City-Test.mmdb', __FILE__)
    end

    let :source_data do
      JSON.load(File.read(db_path.sub('test-data', 'source-data').sub('.mmdb', '.json'))).map { |r| normalize_source_record(r) }
    end

    def normalize_source_record(record)
      record.each_value do |v|
        %w[city country continent registered_country].each do |p|
          %w[geoname_id confidence].each do |pp|
            if v[p] && v[p][pp]
              v[p][pp] = Integer(v[p][pp])
            end
          end
        end
        if (subdivisions = v['subdivisions'])
          subdivisions.each do |subdivision|
            %w[geoname_id confidence].each do |p|
              if subdivision[p]
                subdivision[p] = Integer(subdivision[p])
              end
            end
          end
        end
        if (location = v['location'])
          %w[latitude longitude].each do |p|
            if location[p]
              location[p] = Float(location[p])
            end
          end
          %w[accuracy_radius metro_code].each do |p|
            if location[p]
              location[p] = Integer(location[p])
            end
          end
        end
        if (postal = v['postal'])
          %w[confidence].each do |p|
            if postal[p]
              postal[p] = Integer(postal[p])
            end
          end
        end
        if (traits = v['traits'])
          %w[autonomous_system_number].each do |p|
            if traits[p]
              traits[p] = Integer(traits[p])
            end
          end
        end
      end
      record
    end

    def symbolize_keys(v)
      case v
      when Hash
        v.each_with_object({}) { |(k, v), h| h[k.to_sym] = symbolize_keys(v) }
      when Array
        v.map { |vv| symbolize_keys(vv) }
      else
        v
      end
    end

    describe '.open' do
      it 'returns a database' do
        expect(described_class.open(db_path)).to be_a(described_class)
      end

      context 'when given a path that does not exist' do
        it 'raises an error' do
          expect { described_class.open('foobaz') }.to raise_error(IOError, /foobaz \(no such file or directory\)/i)
          expect { described_class.open(nil) }.to raise_error(ArgumentError, /path is required/i)
        end
      end

      context 'when given a malformed database' do
        it 'raises an error' do
          expect { described_class.open(__FILE__) }.to raise_error(MalformedDatabaseError, /metadata section not found/i)
        end
      end
    end

    describe '#close' do
      it 'raises an error when #get is called after #close' do
        database.close
        expect { database.get('1.1.1.1') }.to raise_error(IOError, /closed/)
      end
    end

    describe '#metadata' do
      context 'returns an object that' do
        it 'knows the database format version' do
          expect(database.metadata.format_version).to eq('2.0')
        end

        it 'knows the build time of the database' do
          expect(database.metadata.build_time).to eq(Time.utc(2016, 2, 19, 16, 51, 56))
        end

        it 'knows the database type' do
          expect(database.metadata.database_type).to eq('GeoIP2-City')
        end

        it 'knows the database languages' do
          expect(database.metadata.languages).to contain_exactly('en', 'zh')
        end

        it 'knows the database description' do
          expect(database.metadata.description).to eq('GeoIP2 City Test Database (fake GeoIP2 data, for example purposes only)')
        end

        it 'knows the database description in multiple languages' do
          expect(database.metadata.description('en')).to eq('GeoIP2 City Test Database (fake GeoIP2 data, for example purposes only)')
          expect(database.metadata.description('zh')).to eq("小型数据库")
        end

        it 'knows the IP version of the database' do
          expect(database.metadata.ip_version).to eq(6)
        end
      end
    end

    describe '#get' do
      let :record do
        source_data[Integer(source_data.length/2)]
      end

      it 'returns the record that corresponds to the specified IP address' do
        ip = record.keys.first.split('/').first
        expect(database.get(ip)).to eq(record.values.first)
      end

      it 'returns nil when no record exists for an IP address' do
        expect(database.get('1.2.3.4')).to be_nil
      end

      context 'when the :symbolize_keys option is set' do
        let :database do
          described_class.open(db_path, symbolize_keys: true)
        end

        it 'returns the record with all its hash keys as symbols instead of strings' do
          ip = record.keys.first.split('/').first
          expect(database.get(ip)).to eq(symbolize_keys(record.values.first))
        end
      end

      context 'when given something that is not an IP address' do
        it 'raises an error' do
          expect { database.get('hello world') }.to raise_error(ArgumentError, /hello world/i)
        end
      end

      %w[IPv4 IPv6 mixed].each do |ip_version|
        %w[24 28 32].each do |bit_length|
          context "when the database is for #{ip_version} IP numbers and the nodes are #{bit_length} bits" do
            let :db_path do
              File.expand_path("../../resources/maxmind-db/test-data/MaxMind-DB-test-#{ip_version.downcase}-#{bit_length}.mmdb", __FILE__)
            end

            unless ip_version == 'IPv6'
              it 'returns the record that corresponds to the specified IPv4 address' do
                %w[1.1.1.1 1.1.1.2 1.1.1.4 1.1.1.8].each do |address|
                  expected_address = ip_version == 'mixed' ? "::#{address}" : address
                  expect(database.get(address)).to eq({'ip' => expected_address})
                end
              end
            end

            unless ip_version == 'IPv4'
              it 'returns the record that corresponds to the specified IPv6 address' do
                %w[::1:ffff:ffff ::2:0:0 ::2:0:40 ::2:0:50 ::2:0:58].each do |address|
                  expect(database.get(address)).to eq({'ip' => address})
                end
              end
            end
          end
        end
      end
    end
  end
end
