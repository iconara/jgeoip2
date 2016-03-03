module JGeoIP2
  describe Database do
    let :database do
      described_class.open(db_path)
    end

    let :db_path do
      File.expand_path('../../resources/GeoLite2-City.mmdb', __FILE__)
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

    describe '#metadata' do
      context 'returns an object that' do
        it 'knows the database format version' do
          expect(database.metadata.format_version).to eq('2.0')
        end

        it 'knows the build time of the database' do
          expect(database.metadata.build_time).to eq(Time.at(1456985563))
        end

        it 'knows the database type' do
          expect(database.metadata.database_type).to eq('GeoLite2-City')
        end

        it 'knows the database languages' do
          expect(database.metadata.languages).to contain_exactly('de', 'en', 'es', 'fr', 'ja', 'pt-BR', 'ru', 'zh-CN')
        end

        it 'knows the database description' do
          expect(database.metadata.description).to eq('GeoLite2 City database')
        end

        it 'knows the database description in multiple languages' do
          expect(database.metadata.description('en')).to eq('GeoLite2 City database')
          expect(database.metadata.description('de')).to be_nil # documentation only, GeoLite2 City only has an english description
        end

        it 'knows the IP version of the database' do
          expect(database.metadata.ip_version).to eq(6)
        end
      end
    end

    describe '#get' do
      it 'returns a hash' do
        expect(database.get('212.116.72.249')).to be_a(Hash)
      end

      context 'when given a malformed IP' do
        it 'raises an error'
      end
    end
  end
end
