import {
  FilterParams,
  normalizeFilterParams,
} from '../../src/utils/searchFilters';
import { expect } from 'chai';

describe('normalizeFilterParams', () => {
  it('should remove empty arrays', () => {
    const filterParams: FilterParams = {
      people: [],
      itemTypes: [],
      networks: [],
      genresFilter: [],
    };

    const newFilters = normalizeFilterParams(filterParams);

    expect(newFilters.genresFilter).to.be.undefined;
    expect(newFilters.itemTypes).to.be.undefined;
    expect(newFilters.people).to.be.undefined;
    expect(newFilters.networks).to.be.undefined;
  });

  it('should remove empty sliders', () => {
    const filterParams: FilterParams = {
      sliders: {},
    };

    const newFilters1 = normalizeFilterParams(filterParams);

    expect(newFilters1.sliders).to.be.undefined;

    const filterParams2: FilterParams = {
      sliders: {
        imdbRating: { min: undefined, max: undefined },
        releaseYear: {},
      },
    };

    const newFilters2 = normalizeFilterParams(filterParams2);
    expect(newFilters2.sliders).to.be.undefined;
  });
});
