import { FilterParams } from '../../utils/searchFilters';
import React, {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import Head from 'next/head';
import AppWrapper from '../../containers/AppWrapper';
import useCustomCompareMemo from '../../hooks/useMemoCompare';
import _ from 'lodash';

interface ContextType {
  beans: number;
  setBeans: (n: number) => void;
}

const TestContext = createContext<ContextType>({
  beans: 1,
  setBeans: () => {},
});

function WithBeans({ children }) {
  const [beans, setBeans] = useState<number>(1);

  // const memoedUserState = useCustomCompareMemo(
  //   () => ({ beans, setBeans }),
  //   [beans],
  //   _.isEqual,
  // );
  //
  const memoedUserState = useMemo(() => ({ beans, setBeans }), [beans]);

  return (
    <TestContext.Provider value={memoedUserState}>
      {children}
    </TestContext.Provider>
  );
}

function makeExploreWrapper(defaultFilters: FilterParams) {
  function ExploreWrapper() {
    const [beans, setBeans] = useState<number>(1);
    return (
      <React.Fragment>
        <Head>
          <title>Explore - Popular</title>
        </Head>
        <AppWrapper hideFooter>
          <TestContext.Provider value={{ beans, setBeans }}>
            <InnerComponent />
          </TestContext.Provider>
        </AppWrapper>
      </React.Fragment>
    );
  }

  return ExploreWrapper;
}

function InnerComponent() {
  const { beans, setBeans } = useContext(TestContext);
  const ref = useRef(beans);

  useEffect(() => {
    if (beans !== ref.current) {
      console.log('beans changed');
      ref.current = beans;
    }
  }, [beans]);

  return (
    <div>
      <h1>Inner Component</h1>
      <button onClick={() => setBeans(beans + 1)}>Button</button>
    </div>
  );
}

export default makeExploreWrapper({ sortOrder: 'popularity' });
