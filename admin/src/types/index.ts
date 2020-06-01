// prettier-ignore
export type DeepReadonly<T> =
  T extends (infer R)[] ? DeepReadonlyArray<R> :
    T extends Function ? T :
      T extends object ? DeepReadonlyObject<T> :
        T;

export interface DeepReadonlyArray<T> extends ReadonlyArray<DeepReadonly<T>> {}

export type DeepReadonlyObject<T> = {
  readonly [P in keyof T]: DeepReadonly<T[P]>;
};

export type PotentialMatch = DeepReadonly<_PotentialMatch>;

interface _PotentialMatch {
  id: string;
  created_at: string;
  potential: _PotentialMatchStorageItem;
  scraped: _PotentialMatchScrapedItem;
}

export type PotentialMatchStorageItem = Readonly<_PotentialMatchStorageItem>;

interface _PotentialMatchStorageItem {
  id: string;
  original_title?: string;
  title: string;
  release_date?: string;
  external_ids: string;
  type: string;
  slug?: string;
  images?: {
    provider_id: number;
    provider_shortname: string;
    id: string;
    image_type: string;
  }[];
}

interface _PotentialMatchScrapedItem {
  type: ScrapeItemType;
  item: {
    availableDate?: string;
    title: string;
    releaseYear?: number;
    network: string;
    status: string;
    externalId?: string;
    description?: string;
    itemType: string;
    url?: string;
    posterImageUrl?: string;
  };
  raw: object;
}

type PotentialMatchScrapedItem = Readonly<_PotentialMatchScrapedItem>;

export enum ScrapeItemType {
  HuluCatalog = 'HuluCatalog',
  HboCatalog = 'HboCatalog',
  NetflixCatalog = 'NetflixCatalog',
  DisneyPlusCatalog = 'DisneyPlusCatalog',
  HboMaxCatalog = 'HboMaxCatalog',
  HboChanges = 'HboChanges',
  NetflixOriginalsArriving = 'NetflixOriginalsArriving',
}
