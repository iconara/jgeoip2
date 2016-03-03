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
      JSON.load(File.read(db_path.sub('test-data', 'source-data').sub('.mmdb', '.json')))
    end

    def normalize_source_record(record)
      record.merge(record) do |k, v, _|
        vv = v.dup
        %w[city country continent].each do |what|
          if vv[what]
            vv[what]['geoname_id'] = Integer(vv[what]['geoname_id'])
          end
        end
        if vv['location']
          vv['location']['latitude'] = Float(vv['location']['latitude'])
          vv['location']['longitude'] = Float(vv['location']['longitude'])
        end
        vv
      end
      record
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
          expect { described_class.open(__FILE__) }.to raise_error(ArgumentError, /malformed database: metadata section not found/i)
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
        normalize_source_record(source_data[103])
      end

      it 'returns the record that corresponds to the specified IP address' do
        ip = record.keys.first.split('/').first
        expect(database.get(ip)).to eq(record.values.first)
      end

      it 'returns nil when no record exists for an IP address' do
        expect(database.get('1.2.3.4')).to be_nil
      end

      context 'when given a malformed IP' do
        it 'raises an error'
      end
    end
  end
end
