import React, { useState } from 'react';
import { CircularProgress, makeStyles, TextField } from '@material-ui/core';
import Autocomplete, { RenderInputParams } from '@material-ui/lab/Autocomplete';
import { useDispatch, useSelector } from 'react-redux';
import _ from 'lodash';
import { searchPeople } from '../../actions/search/person_search';
import { AppState } from '../../reducers';
import { getTmdbProfileImage } from '../../utils/image-helper';
import { truncateText } from '../../utils/textHelper';

const useStyles = makeStyles(theme => ({
  poster: {
    width: 25,
    boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
    marginRight: `${theme.spacing(1)}`,
  },
}));

interface OwnProps {
  selectedCast?: string[];
  handleChange: (change: string[]) => void;
}

type Props = OwnProps;

export default function PersonFilter(props: Props) {
  const classes = useStyles();
  const [open, setOpen] = useState(false);
  const [options, setOptions] = useState<string[]>([]);
  const [inputValue, setInputValue] = useState('');
  const dispatch = useDispatch();
  const peopleSearch = useSelector(
    (state: AppState) => state.search.people,
    _.isEqual,
  );
  const nameBySlugOrId = useSelector(
    (state: AppState) => state.people.nameByIdOrSlug,
  );

  const loading = open && inputValue.length > 0 && peopleSearch.searching;

  const debouncedChange = _.debounce((event, value) => {
    let trimmed = value.trim();
    if (trimmed.length > 0) {
      setInputValue(trimmed);
      dispatch(searchPeople({ query: trimmed, limit: 5 }));
    }
  }, 150);

  const personSelected = (event, people: string[]) => {
    props.handleChange(people);

    setOptions([]);
    setInputValue('');
  };

  const renderSelectOption = (option: string) => {
    let person = _.find(
      peopleSearch.results || [],
      person => person.slug === option || person.id === option,
    );

    if (person) {
      return (
        <React.Fragment>
          <img
            src={
              getTmdbProfileImage(person)
                ? `https://image.tmdb.org/t/p/w92/${getTmdbProfileImage(
                    person,
                  )!}`
                : ''
            }
            className={classes.poster}
          />
          {truncateText(person.name, 30)}
        </React.Fragment>
      );
    } else {
      return null;
    }
  };

  const renderInput = (params: RenderInputParams) => {
    return (
      <TextField
        {...params}
        placeholder="Starring"
        fullWidth
        InputProps={{
          ...params.InputProps,
          endAdornment: (
            <React.Fragment>
              {loading ? <CircularProgress color="inherit" size={20} /> : null}
              {params.InputProps.endAdornment}
            </React.Fragment>
          ),
        }}
      />
    );
  };

  let slugz = (peopleSearch.results || []).map(
    person => person.slug || person.id,
  );

  return (
    <div>
      <Autocomplete
        id="asynchronous-demo"
        style={{ width: 300 }}
        open={open}
        onOpen={() => {
          setOpen(true);
        }}
        onClose={() => {
          setOpen(false);
        }}
        onChange={personSelected}
        onInputChange={debouncedChange}
        getOptionLabel={option => nameBySlugOrId[option]}
        options={slugz}
        filterOptions={opts => opts}
        filterSelectedOptions={false}
        loading={loading}
        multiple
        renderOption={renderSelectOption}
        disablePortal
        renderInput={renderInput}
      />
    </div>
  );
}
