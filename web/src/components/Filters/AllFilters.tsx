import { Collapse, makeStyles, Theme } from '@material-ui/core';
import React from 'react';
import { Genre, ItemType, SortOptions, NetworkType } from '../../types';
import TypeToggle from './TypeToggle';
import NetworkSelect from './NetworkSelect';
import GenreSelect from './GenreSelect';
import SortDropdown from './SortDropdown';
import Sliders, { SliderChange } from './Sliders';
import { FilterParams } from '../../utils/searchFilters';
import { filterParamsEqual } from '../../utils/changeDetection';
import PersonFilter from './PersonFilter';

const useStyles = makeStyles((theme: Theme) => ({
  allFiltersContainer: {
    display: 'flex',
    flexWrap: 'wrap',
    padding: theme.spacing(2),
    backgroundColor: `${theme.palette.grey[800]}`,
  },
  filterSortContainer: {
    [theme.breakpoints.up('sm')]: {
      display: 'flex',
    },
    [theme.breakpoints.down('sm')]: {
      flexDirection: 'column',
    },
    zIndex: 1000,
    marginBottom: theme.spacing(1),
    flexGrow: 1,
  },
  filterTitle: { display: 'block', width: '100%' },
  genreContainer: {
    display: 'flex',
    flexDirection: 'row',
    width: '40%',
    flexWrap: 'wrap',
    margin: `${theme.spacing(1)}px 0`,
    [theme.breakpoints.down('sm')]: {
      width: '100%',
    },
  },
  slidersContainer: {
    display: 'flex',
    flexGrow: 1,
    margin: `${theme.spacing(1)}px 0`,
    alignItems: 'flex-start',
    flexDirection: 'column',
    width: '30%',
  },
  networkContainer: {
    display: 'flex',
    margin: `${theme.spacing(1)}px 0`,
    alignItems: 'flex-start',
    flexGrow: 1,
    flexDirection: 'column',
    width: '30%',
    '& > div': {
      marginBottom: theme.spacing(1),
    },
  },
  toEdgeWrapper: {
    margin: `0 0 ${theme.spacing(2)}px`,
  },
  typeContainer: {
    display: 'flex',
    margin: `${theme.spacing(1)}px 0`,
    alignItems: 'flex-start',
    flexGrow: 1,
  },
  sortContainer: {
    display: 'flex',
    flexDirection: 'column',
    margin: theme.spacing(1),
    alignItems: 'flex-start',
  },
}));

interface Props {
  disableSliders?: boolean;
  disableSortOptions?: boolean;
  disableNetworks?: boolean;
  disableTypeChange?: boolean;
  disableGenres?: boolean;
  updateFilters: (filterParams: FilterParams) => void;
  filters: FilterParams;
  isListDynamic?: boolean;
  genres?: Genre[];
  open: boolean;
  disabledGenres?: number[];
  sortOptions?: SortOptions[];
}

const AllFilters = (props: Props) => {
  const classes = useStyles();
  const TIMEOUT_APPEAR = 600;
  const TIMEOUT_ENTER = 600;
  const TIMEOUT_EXIT = 300;
  const {
    disabledGenres,
    genres,
    isListDynamic,
    open,
    disableSliders,
    disableSortOptions,
    disableNetworks,
    disableTypeChange,
    disableGenres,
    updateFilters,
    filters,
    sortOptions,
  } = props;

  const handleFilterUpdate = (newFilter: FilterParams) => {
    if (!filterParamsEqual(filters, newFilter)) {
      updateFilters(newFilter);
    }
  };

  const setGenre = (genres?: number[]) => {
    handleFilterUpdate({ ...filters, genresFilter: genres });
  };

  const setType = (type?: ItemType[]) => {
    handleFilterUpdate({ ...filters, itemTypes: type });
  };

  const setNetworks = (networks?: NetworkType[]) => {
    handleFilterUpdate({ ...filters, networks });
  };

  const setSort = (sortOrder: SortOptions) => {
    handleFilterUpdate({ ...filters, sortOrder });
  };

  const setSliders = (sliderChange: SliderChange) => {
    let newFilters = {
      ...filters,
      sliders: {
        ...(filters.sliders || {}),
        releaseYear: sliderChange.releaseYear,
      },
    };

    handleFilterUpdate(newFilters);
  };

  const setPeople = (people: string[]) => {
    handleFilterUpdate({
      ...filters,
      people,
    });
  };

  return (
    <Collapse
      in={open}
      timeout={{
        appear: TIMEOUT_APPEAR,
        enter: TIMEOUT_ENTER,
        exit: TIMEOUT_EXIT,
      }}
      className={classes.toEdgeWrapper}
      appear
    >
      <div className={classes.allFiltersContainer}>
        <div className={classes.filterSortContainer}>
          <div className={classes.genreContainer}>
            {!disableGenres && (
              <GenreSelect
                genres={genres}
                disabledGenres={disabledGenres}
                handleChange={setGenre}
                selectedGenres={filters.genresFilter || []}
              />
            )}
          </div>

          <div className={classes.slidersContainer}>
            {!disableSliders ? (
              <Sliders handleChange={setSliders} sliders={filters.sliders} />
            ) : null}
            <PersonFilter
              handleChange={setPeople}
              selectedCast={filters.people}
            />
          </div>

          <div className={classes.networkContainer}>
            {!disableNetworks && (
              <NetworkSelect
                selectedNetworks={filters.networks}
                handleChange={setNetworks}
              />
            )}
            {!disableTypeChange && (
              <TypeToggle
                selectedTypes={filters.itemTypes}
                handleChange={setType}
              />
            )}
            {!disableSortOptions && (
              <SortDropdown
                isListDynamic={!!isListDynamic}
                handleChange={setSort}
                selectedSort={filters.sortOrder}
                validSortOptions={sortOptions}
              />
            )}
          </div>
        </div>
      </div>
    </Collapse>
  );
};

export default AllFilters;
