import { Collapse, makeStyles, Theme, Typography } from '@material-ui/core';
import React from 'react';
import { Genre, ItemType, ListSortOptions, NetworkType } from '../../types';
import TypeToggle from './TypeToggle';
import NetworkSelect from './NetworkSelect';
import GenreSelect from './GenreSelect';
import SortDropdown from './SortDropdown';
import Sliders, { SliderChange } from './Sliders';

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
  handleGenreChange?: (genre?: number[]) => void;
  handleTypeChange?: (type?: ItemType[]) => void;
  handleNetworkChange?: (networkTypes?: NetworkType[]) => void;
  handleSortChange?: (sortOrder: ListSortOptions) => void;
  handleSliderChange?: (sliderChange: SliderChange) => void;
  isListDynamic?: boolean;
  genres?: Genre[];
  open: boolean;
  disabledGenres?: number[];
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
    handleGenreChange,
    handleNetworkChange,
    handleTypeChange,
    handleSortChange,
    handleSliderChange,
  } = props;

  const setGenre = (genres?: number[]) => {
    handleGenreChange && handleGenreChange(genres);
  };

  const setType = (type?: ItemType[]) => {
    handleTypeChange && handleTypeChange(type);
  };

  const setNetworks = (networks?: NetworkType[]) => {
    handleNetworkChange && handleNetworkChange(networks);
  };

  const setSort = (sortOrder: ListSortOptions) => {
    handleSortChange && handleSortChange(sortOrder);
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
            {handleGenreChange && (
              <GenreSelect
                genres={genres}
                disabledGenres={disabledGenres}
                handleChange={setGenre}
              />
            )}
          </div>
          {handleSliderChange ? (
            <div className={classes.slidersContainer}>
              <Sliders handleChange={handleSliderChange} />
            </div>
          ) : null}
          <div className={classes.networkContainer}>
            {handleNetworkChange && (
              <NetworkSelect handleChange={setNetworks} />
            )}
            {handleTypeChange && <TypeToggle handleChange={setType} />}
            {handleSortChange && (
              <SortDropdown
                isListDynamic={!!isListDynamic}
                handleChange={setSort}
              />
            )}
          </div>
        </div>
      </div>
    </Collapse>
  );
};

export default AllFilters;
